# المرحلة الثانية — ربط واجهة Flutter بمحرك Android

## ما تم في هذه المرحلة
- استبدال قائمة التطبيقات التجريبية بواجهة تستدعي عقد `MethodChannel` وتعرض التطبيقات وحالة القفل التي يعيدها Android.
- إضافة تحديث للقائمة، وبحث بالاسم أو اسم الحزمة، ورسائل نجاح/فشل لا تغيّر الحالة محليًا عند فشل العملية.
- إضافة `android_bridge/AppGuardFlutterBridge.kt` كتطبيق فعلي لعقد الربط باستخدام `AppGuardApplication.repository` وRoom الحالية.
- عدم إنشاء قاعدة بيانات ثانية وعدم نقل PIN أو بيانات الحماية إلى Dart.
- إبقاء ملف جسر Kotlin خارج `app/src/main` مؤقتًا حتى لا تنكسر نسخة Android الأصلية قبل إعداد Flutter Gradle embedding.

## عقد القناة
الاسم: `com.example.app_guard/protection`

- `isProtectionBridgeReady` → Boolean
- `getDashboardStatus` → `protectionEnabled`, `globalLockActive`, `recentAppsLockEnabled`, `recentAppsAlwaysLocked`
- `getInstalledApps` → قائمة `packageName`, `appName`, `isLocked`, `isSystemApp`
- `setAppLocked` ← `packageName`, `locked` → Boolean
- `setProtectionEnabled` ← `enabled` → Boolean
- `refreshInstalledApps` → Boolean

## ما لم يُنفذ بعد
هذا لا يزال تكاملًا مرحليًا: لم يتم إنشاء Flutter Android embedding أو تحويل نقطة التشغيل إلى FlutterActivity، لأن Flutter SDK غير متوفر في بيئة التنفيذ. لا تنقل جسر Kotlin إلى `app/src/main` قبل إضافة Flutter Gradle plugin/embedding وإعداد النشاط المضيف. لا تزال شاشات الملفات والجداول والإعدادات غير مربوطة بعملياتها الأصلية.

## تحقق قبل الدمج النهائي
1. تشغيل `flutter pub get` ثم `flutter analyze` و`flutter test` داخل `flutter_ui`.
2. إعداد Flutter Gradle embedding في بيئة Flutter رسمية.
3. ربط الجسر في FlutterEngine واحد وتحريره عند إيقاف المضيف.
4. اختبار إظهار التطبيقات، حفظ القفل، إعادة فتح الشاشة، والتأكد من بقاء الحالة من Room.
5. اختبار أن تعطيل الحماية لا يغيّر إعدادات PIN أو يمحو التطبيقات المقفلة.
6. بناء Android debug وتشغيل اختبارات Kotlin الأصلية قبل اعتماد التحويل.
