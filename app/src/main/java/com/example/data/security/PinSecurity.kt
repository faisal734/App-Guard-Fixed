package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PIN verifier with persisted throttling and a deliberately versioned hash format.
 * Legacy SHA-256 PINs are upgraded transparently after a successful verification.
 */
class PinSecurity(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext
        .getSharedPreferences("app_guard_sec_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
        private const val KEY_COOLDOWN_UNTIL_ELAPSED = "cooldown_until_elapsed"
        private const val KEY_COOLDOWN_DURATION_SECONDS = "cooldown_duration_seconds"
        private const val KEY_COOLDOWN_BOOT_COUNT = "cooldown_boot_count"
        private const val HASH_PREFIX = "pbkdf2_sha256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val HASH_BYTES = 32
        private const val SALT_BYTES = 16
        private const val MIN_PIN_LENGTH = 4
        private const val MAX_PIN_LENGTH = 12

        private val stateLock = Any()
    }

    fun isPinSet(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    /** Accept numeric PINs only; the lock screen keypad is numeric. */
    fun setPin(pin: String): Boolean {
        if (pin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH || pin.any { it !in '0'..'9' }) return false
        return synchronized(stateLock) {
            val salt = generateSalt()
            val hash = derivePinHash(pin, salt)
            prefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_COOLDOWN_UNTIL, 0L)
                .putLong(KEY_COOLDOWN_UNTIL_ELAPSED, 0L)
                .putLong(KEY_COOLDOWN_DURATION_SECONDS, 0L)
                .putInt(KEY_COOLDOWN_BOOT_COUNT, currentBootCount(appContext))
                .commit()
        }
    }

    /**
     * Returns false without hashing while a cooldown is active. This enforcement lives in
     * the security layer, not only in the UI, so direct callers cannot bypass the delay.
     */
    fun verifyPin(pin: String, currentTime: Long = System.currentTimeMillis()): Boolean = synchronized(stateLock) {
        if (getRemainingCooldownSeconds(currentTime) > 0L) return@synchronized false
        if (pin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH || pin.any { it !in '0'..'9' }) {
            recordFailedAttemptLocked(currentTime)
            return@synchronized false
        }

        val storedHash = prefs.getString(KEY_PIN_HASH, null)
        val saltString = prefs.getString(KEY_PIN_SALT, null)
        if (storedHash == null || saltString == null) {
            recordFailedAttemptLocked(currentTime)
            return@synchronized false
        }

        val salt = try {
            Base64.decode(saltString, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            recordFailedAttemptLocked(currentTime)
            return@synchronized false
        }
        if (salt.size != SALT_BYTES) {
            recordFailedAttemptLocked(currentTime)
            return@synchronized false
        }

        val valid = try {
            if (storedHash.startsWith("$HASH_PREFIX\$")) {
                val parts = storedHash.split('$')
                val iterations = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size != 3 || iterations == null || iterations !in 10_000..2_000_000) {
                    false
                } else {
                    val expected = Base64.decode(parts[2], Base64.NO_WRAP)
                    val actual = pbkdf2HmacSha256(pin.toByteArray(Charsets.UTF_8), salt, iterations, HASH_BYTES)
                    MessageDigest.isEqual(expected, actual)
                }
            } else {
                // Backward compatibility for hashes created by previous versions.
                val legacy = legacySha256(pin, salt)
                MessageDigest.isEqual(storedHash.toByteArray(Charsets.UTF_8), legacy.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            false
        }

        if (!valid) {
            recordFailedAttemptLocked(currentTime)
            return@synchronized false
        }

        // Upgrade older SHA-256 hashes on successful login without forcing a PIN reset.
        if (!storedHash.startsWith("$HASH_PREFIX\$")) {
            prefs.edit().putString(KEY_PIN_HASH, derivePinHash(pin, salt)).commit()
        }
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).also(::clearCooldown).commit()
        true
    }

    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0)

    fun getRemainingCooldownSeconds(currentTime: Long = System.currentTimeMillis()): Long = synchronized(stateLock) {
        val duration = prefs.getLong(KEY_COOLDOWN_DURATION_SECONDS, 0L)
        if (duration <= 0L) {
            // Compatibility with a cooldown stored by an older app version.
            val legacyUntil = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
            if (legacyUntil <= currentTime) return@synchronized 0L
            return@synchronized ((legacyUntil - currentTime + 999L) / 1000L).coerceAtLeast(0L)
        }

        val currentBoot = currentBootCount(appContext)
        val storedBoot = prefs.getInt(KEY_COOLDOWN_BOOT_COUNT, -1)
        val nowElapsed = SystemClock.elapsedRealtime()
        var untilElapsed = prefs.getLong(KEY_COOLDOWN_UNTIL_ELAPSED, 0L)

        if (currentBoot >= 0 && currentBoot != storedBoot) {
            // Reboot during a cooldown: restart the remaining penalty rather than allowing a
            // reboot or a wall-clock change to bypass it. This runs only when boot count changes.
            untilElapsed = nowElapsed + duration * 1000L
            prefs.edit()
                .putLong(KEY_COOLDOWN_UNTIL_ELAPSED, untilElapsed)
                .putLong(KEY_COOLDOWN_UNTIL, currentTime + duration * 1000L)
                .putInt(KEY_COOLDOWN_BOOT_COUNT, currentBoot)
                .commit()
        }

        if (currentBoot < 0) {
            // Best-effort fallback for unusual devices that do not expose BOOT_COUNT.
            val wallUntil = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
            if (wallUntil <= currentTime) {
                clearCooldownLocked()
                return@synchronized 0L
            }
            return@synchronized ((wallUntil - currentTime + 999L) / 1000L).coerceAtLeast(0L)
        }

        val remainingMillis = untilElapsed - nowElapsed
        if (remainingMillis <= 0L) {
            clearCooldownLocked()
            return@synchronized 0L
        }
        (remainingMillis + 999L) / 1000L
    }

    private fun recordFailedAttemptLocked(currentTime: Long) {
        val attempts = (getFailedAttempts() + 1).coerceAtMost(Int.MAX_VALUE)
        val cooldownSeconds = when {
            attempts >= 10 -> 600L
            attempts >= 8 -> 300L
            attempts >= 5 -> 120L
            attempts >= 3 -> 30L
            else -> 0L
        }
        val until = if (cooldownSeconds > 0L) currentTime + cooldownSeconds * 1000L else 0L
        val untilElapsed = if (cooldownSeconds > 0L) {
            SystemClock.elapsedRealtime() + cooldownSeconds * 1000L
        } else 0L
        // commit() makes the rate-limit state durable before returning to the caller.
        val editor = prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_COOLDOWN_UNTIL, until)
            .putLong(KEY_COOLDOWN_UNTIL_ELAPSED, untilElapsed)
            .putLong(KEY_COOLDOWN_DURATION_SECONDS, cooldownSeconds)
            .putInt(KEY_COOLDOWN_BOOT_COUNT, currentBootCount(appContext))
        editor.commit()
    }

    fun resetFailedAttempts() {
        synchronized(stateLock) {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).also(::clearCooldown).commit()
        }
    }

    /** Used by the built-in self-test to restore the previous credential record. */
    fun backupCredentials(): Pair<String?, String?> = synchronized(stateLock) {
        Pair(prefs.getString(KEY_PIN_HASH, null), prefs.getString(KEY_PIN_SALT, null))
    }

    fun restoreCredentials(hash: String?, salt: String?) {
        synchronized(stateLock) {
            val editor = prefs.edit()
            if (hash != null && salt != null) {
                editor.putString(KEY_PIN_HASH, hash)
                editor.putString(KEY_PIN_SALT, salt)
            } else {
                editor.remove(KEY_PIN_HASH)
                editor.remove(KEY_PIN_SALT)
            }
            editor.putInt(KEY_FAILED_ATTEMPTS, 0)
            clearCooldown(editor)
            editor.commit()
        }
    }

    private fun clearCooldownLocked() {
        prefs.edit().also(::clearCooldown).commit()
    }

    private fun clearCooldown(editor: SharedPreferences.Editor) {
        editor.putLong(KEY_COOLDOWN_UNTIL, 0L)
            .putLong(KEY_COOLDOWN_UNTIL_ELAPSED, 0L)
            .putLong(KEY_COOLDOWN_DURATION_SECONDS, 0L)
            .putInt(KEY_COOLDOWN_BOOT_COUNT, currentBootCount(appContext))
    }

    private fun currentBootCount(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: Exception) {
        -1
    }

    private fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun derivePinHash(pin: String, salt: ByteArray): String {
        val derived = pbkdf2HmacSha256(pin.toByteArray(Charsets.UTF_8), salt, PBKDF2_ITERATIONS, HASH_BYTES)
        return "$HASH_PREFIX\$$PBKDF2_ITERATIONS\$${Base64.encodeToString(derived, Base64.NO_WRAP)}"
    }

    /** PBKDF2-HMAC-SHA256 implemented with Android's standard Mac provider. */
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, outputBytes: Int): ByteArray {
        require(iterations > 0 && outputBytes > 0)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val blockCount = (outputBytes + mac.macLength - 1) / mac.macLength
        val output = ByteArray(blockCount * mac.macLength)
        for (block in 1..blockCount) {
            mac.update(salt)
            val counter = byteArrayOf(
                (block ushr 24).toByte(), (block ushr 16).toByte(),
                (block ushr 8).toByte(), block.toByte()
            )
            var u = mac.doFinal(counter)
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            System.arraycopy(t, 0, output, (block - 1) * mac.macLength, mac.macLength)
        }
        return output.copyOf(outputBytes)
    }

    private fun legacySha256(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return Base64.encodeToString(digest.digest(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}
