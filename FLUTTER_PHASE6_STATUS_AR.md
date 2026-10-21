# المرحلة السادسة — فحص ما قبل الدمج الفعلي

## ما أُضيف
- `tools/phase6_preflight.sh`: فحص موحّد لتوفر Flutter/Dart/Java، وتوافق عمليات MethodChannel، وصياغة سكربت التحضير، ووجود ملفات الدمج الأساسية وسلامة XML.
- الفحص يوضح صراحةً أن نجاحه لا يعادل بناء APK أو اختبار حماية على جهاز.

## نتائج البيئة الحالية
- فحص ثابت للجسر: ناجح؛ 12 عملية Dart لها معالجات Kotlin مقابلة.
- XML الخاص بـ AndroidManifest: سليم.
- صياغة سكربت التحضير: سليمة.
- Flutter: غير مثبت/غير موجود في PATH.
- Dart: غير مثبت/غير موجود في PATH.
- بناء Gradle: متعذر قبل بدء البناء؛ محاولة تنزيل Gradle 9.3.1 فشلت بسبب `UnknownHostException: services.gradle.org`.

## ما لم يتم ادعاؤه
لم يتم إنشاء APK، ولم يتم تشغيل `flutter analyze` أو `flutter test`، ولم يتم اختبار PIN أو Accessibility أو Recent على جهاز فعلي. لذلك لا تعتبر هذه الحزمة إصدارًا نهائيًا للحماية.

## التشغيل على جهاز تطوير متصل
من جذر المشروع:
```bash
bash tools/phase6_preflight.sh
bash tools/prepare_flutter_phase4.sh
(cd flutter_module && flutter pub get && flutter analyze && flutter test)
./gradlew -PenableFlutterHost=true :app:assembleDebug
```
إذا فشل الفحص بسبب عدم توفر Flutter/Dart أو الشبكة، عالج المتطلبات أولًا. لا تغيّر MainActivity لتصبح شاشة Flutter الرئيسية قبل نجاح البناء واختبار مسارات الحماية على جهاز حقيقي.
