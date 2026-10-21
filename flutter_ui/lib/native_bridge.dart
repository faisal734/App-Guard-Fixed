import 'package:flutter/services.dart';

/// MethodChannel contract for the Android/Kotlin protection engine.
/// Calls fail closed when the native bridge is absent; the UI must never claim
/// a setting was saved unless Android confirms the operation.
class AppGuardNativeBridge {
  AppGuardNativeBridge._();
  static final AppGuardNativeBridge instance = AppGuardNativeBridge._();
  static const MethodChannel _channel = MethodChannel('com.example.app_guard/protection');

  Future<bool> get isAvailable async {
    try {
      return await _channel.invokeMethod<bool>('isProtectionBridgeReady') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<Map<String, dynamic>?> getDashboardStatus() async {
    try {
      final value = await _channel.invokeMapMethod<String, dynamic>('getDashboardStatus');
      return value;
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  Future<List<Map<String, dynamic>>> getInstalledApps() async {
    try {
      final values = await _channel.invokeListMethod<dynamic>('getInstalledApps');
      return (values ?? const []).whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
    } on PlatformException {
      return const [];
    } on MissingPluginException {
      return const [];
    }
  }

  Future<bool> setAppLocked(String packageName, bool locked) async {
    try {
      return await _channel.invokeMethod<bool>('setAppLocked', {
        'packageName': packageName,
        'locked': locked,
      }) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<bool> setProtectionEnabled(bool enabled) async {
    try {
      return await _channel.invokeMethod<bool>('setProtectionEnabled', {'enabled': enabled}) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<List<Map<String, dynamic>>> getProfiles() async {
    try {
      final values = await _channel.invokeListMethod<dynamic>('getProfiles');
      return (values ?? const []).whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
    } on PlatformException {
      return const [];
    } on MissingPluginException {
      return const [];
    }
  }

  Future<bool> activateProfile(int? profileId) async {
    try {
      return await _channel.invokeMethod<bool>('activateProfile', {'profileId': profileId}) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<List<Map<String, dynamic>>> getSchedules() async {
    try {
      final values = await _channel.invokeListMethod<dynamic>('getSchedules');
      return (values ?? const []).whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
    } on PlatformException {
      return const [];
    } on MissingPluginException {
      return const [];
    }
  }

  Future<bool> setScheduleEnabled(int id, bool enabled) async {
    try {
      return await _channel.invokeMethod<bool>('setScheduleEnabled', {'id': id, 'enabled': enabled}) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<bool> setRecentAppsLockEnabled(bool enabled) async {
    try {
      return await _channel.invokeMethod<bool>('setRecentAppsLockEnabled', {'enabled': enabled}) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<bool> setRecentAppsAlwaysLocked(bool enabled) async {
    try {
      return await _channel.invokeMethod<bool>('setRecentAppsAlwaysLocked', {'enabled': enabled}) ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<bool> refreshInstalledApps() async {
    try {
      return await _channel.invokeMethod<bool>('refreshInstalledApps') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}
