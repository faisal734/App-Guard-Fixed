# خطة تحويل حارس التطبيقات إلى Flutter — المرحلة الأولى

## النسخة المرجعية
`App-Guard-Hardened-B-Recent-Protection-Fixed.zip` هي المرجع الوحيد لهذه العملية. لم يتم استبدال الملفات الأصلية أو حذفها.

## ما أُضيف
- مشروع واجهة Flutter مستقل داخل `flutter_ui/`.
- واجهة عربية RTL أولية بخمس وجهات: الرئيسية، التطبيقات، ملفات الحماية، الجداول، الإعدادات.
- واجهة جسر Dart بعقد `MethodChannel` المقترح: `com.example.app_guard/protection`.
- تنبيه واضح في الواجهة بأن البيانات/الأزرار التجريبية ليست حماية فعلية قبل تنفيذ الجسر.

## ما لم يتم فعله عمدًا
- لم يتم تغيير `MainActivity` أو نقطة تشغيل Android.
- لم يتم نقل محرك الحماية أو Room أو PIN إلى Dart.
- لم يتم توصيل قناة MethodChannel في Kotlin بعد.
- لم يتم تعديل مخطط قاعدة البيانات أو الصلاحيات أو الخدمات.
- لم يتم ادعاء نجاح البناء أو الاختبارات.

## عقد الربط المقترح
- `isProtectionBridgeReady` → Boolean
- `getDashboardStatus` → Map بحالة الحماية والملفات والتطبيقات
- `getInstalledApps` → List<Map> للتطبيقات المثبتة وحالة القفل
- `setAppLocked` → `{packageName, locked}` ثم Boolean
- `setProtectionEnabled` → `{enabled}` ثم Boolean

## تقسيم المسؤوليات المستهدف
### Flutter
الواجهات، التنقل، البحث والتصفية البصرية، رسائل الحالة، إدارة النماذج وعرض البيانات.

### Kotlin / Android
Accessibility Service، شاشة القفل، اكتشاف التطبيقات، Room ومصدر البيانات، PIN ومهل المحاولات، Recent، الإشعارات، الجداول، الأذونات، القياسات الحيوية والمهام الخلفية.

## المخاطر ووسيلة التحقق
1. خطر ازدواج مصدر الحالة: تبقى Room وProtectionRepository المصدر الوحيد للحقيقة؛ لا تُنشأ نسخة بيانات أمنية موازية في Flutter.
2. خطر كسر الحماية أثناء تبديل نقطة التشغيل: لا تُبدّل `MainActivity` إلى `FlutterActivity` حتى يكتمل الجسر ويُختبر كل مسار حرج.
3. خطر عدم تطابق عقد القناة: اختبار كل method على Android، ومعالجة `PlatformException` وعدم اعتبار فشل الاستدعاء نجاحًا.
4. التحقق: `flutter analyze` و`flutter test` ثم `./gradlew testDebugUnitTest` و`assembleDebug`، وبعدها اختبار فعلي لخدمة Accessibility والقفل وRecent.

## الحالة الحالية
هذه مرحلة تأسيس واجهة Flutter وليست تحويلًا مكتملًا. بيئة التنفيذ المستخدمة لا تحتوي على Flutter SDK، لذلك لم يمكن تشغيل `flutter pub get`, `flutter analyze` أو `flutter test`. يجب تثبيت Flutter SDK في بيئة البناء قبل دمج الواجهة مع تطبيق Android.

## المرحلة الرابعة — تجهيز الدمج الاختياري
- أضيف نشاط `AppGuardFlutterActivity` لتشغيل واجهة Flutter دون استبدال Launcher الأصلي.
- أضيف سكربت `tools/prepare_flutter_phase4.sh` لإنشاء Flutter add-to-app module رسمي ونسخ واجهة Dart وإعداد الدمج الاختياري.
- يبقى الدمج غير مُفعّل افتراضيًا؛ تشغيله يتطلب Flutter SDK وتنفيذ السكربت والتحقق من البناء.
- لا تُحوّل نقطة التشغيل الرئيسية قبل اختبار PIN، onboarding، الصلاحيات، Accessibility، Recent وحماية التطبيق الذاتي.
