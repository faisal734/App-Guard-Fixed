# المرحلة الثامنة — Flutter واجهة الاستخدام الرئيسية

## ما تم تغييره
- أصبحت واجهة Flutter هي لوحة الاستخدام الأساسية بعد اجتياز إعداد PIN وشاشة التهيئة/الأذونات الأصليتين.
- بقي `MainActivity` نقطة دخول Android مقصودة لتجنب تجاوز إعداد PIN أو شاشة التهيئة عند أول تشغيل.
- عند تفعيل البناء المدمج `-PenableFlutterHost=true`، ينتقل `MainActivity` تلقائيًا إلى `AppGuardFlutterActivity` بعد إعداد PIN وإكمال التهيئة.
- في البناء الأصلي الافتراضي، يبقى السلوك السابق كما هو لأن `FLUTTER_HOST_ENABLED` تكون `false`.
- تمت المحافظة على محرك الحماية وRoom وخدمة Accessibility في Kotlin، ولا يتم نقل قرارات الحماية إلى Dart.
- أضيف `FLAG_SECURE` إلى نافذة Flutter، وفحص جلسة الحماية الذاتية عند العودة إلى الواجهة.
- أثناء الانتقال المقصود من بوابة PIN/التهيئة إلى Flutter لا تُلغى جلسة الفتح المؤقت؛ أما عند الخلفية الحقيقية فيستمر إلغاؤها.

## التحقق المنفذ
- `bash -n tools/prepare_flutter_phase4.sh` و`bash -n tools/build_android_ci.sh`: نجاح.
- `python3 -m py_compile tools/validate_flutter_bridge.py tools/activate_flutter_launcher.py`: نجاح.
- `python3 tools/validate_flutter_bridge.py`: 12 عملية MethodChannel متوافقة في الفحص الثابت.
- تحليل XML لملف AndroidManifest: نجاح.
- لم يُنفذ بناء Android/Flutter أو اختبار جهاز؛ Flutter/Dart غير متوفرين في بيئة التحرير.

## البناء السحابي
شغّل GitHub Actions workflow المسمى `App Guard Android + Flutter build`. يقوم بتثبيت Flutter وJDK، ثم إعداد module وتحليل Dart وتشغيل الاختبارات وبناء APK مع `-PenableFlutterHost=true`.

## شروط القبول قبل إصدار نهائي
1. نجاح `flutter analyze` و`flutter test` و`assembleDebug` في CI.
2. تجربة إعداد PIN أول مرة، ثم التأكد أن Flutter لا يفتح قبل إكمال التهيئة.
3. تجربة قفل التطبيق الذاتي عند الخلفية والعودة.
4. اختبار Accessibility وقفل التطبيقات وRecent على جهاز حقيقي.
5. التأكد من عدم حدوث تكرار/حلقة في شاشة PIN عند ظهور شاشة القفل.

**الحالة:** تم إعداد انتقال واجهة الاستخدام إلى Flutter في مسار البناء المدمج، لكنه غير معتمد نهائيًا حتى ينجح البناء واختبارات الجهاز.
