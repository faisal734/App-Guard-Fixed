# المرحلة الرابعة — تجهيز الدمج الفعلي لـ Flutter مع Android

## الحالة
هذه الحزمة تضيف نشاط Flutter اختياريًا وجسر Kotlin وخطوة إعداد آلية، مع إبقاء `MainActivity` الأصلية نقطة التشغيل. لم يُنفّذ الدمج داخل بيئة الإنشاء الحالية لأن Flutter SDK غير مثبت؛ لا تعتبر الحزمة APK جاهزًا أو أن الدمج اجتاز البناء.

## ما أضيف
- `flutter_ui/android_host/.../AppGuardFlutterActivity.kt`: نشاط Flutter اختياري يسجّل الجسر عند تهيئة المحرك ويتخلص منه عند إغلاق المحرك.
- نسخة مصدر للجسر في `flutter_ui/android_host/.../AppGuardFlutterBridge.kt`.
- `tools/prepare_flutter_phase4.sh`: ينشئ Flutter module رسميًا باستخدام `flutter create --template module`، وينسخ واجهة Dart إليه، ثم يضيف إعدادات دمج اختيارية وملفات Android host.
- دمج اختياري فقط عبر الخاصية `-PenableFlutterHost=true`؛ البناء الأصلي الافتراضي يبقى على مساره الحالي.

## طريقة الاستخدام على جهاز تطوير يحتوي Flutter SDK
1. فك ضغط المشروع.
2. نفّذ من جذر المشروع: `./tools/prepare_flutter_phase4.sh`.
3. نفّذ: `cd flutter_module && flutter pub get`.
4. تحقق من البناء: `./gradlew -PenableFlutterHost=true :app:assembleDebug`.
5. اختبر نشاط Flutter من مسار تطوير داخلي، مع إبقاء `MainActivity` الأصلية Launcher.

## تحذيرات مهمة
- لا تبدّل `MainActivity` إلى Flutter قبل نقل وفحص مسار إعداد PIN، شاشة القفل الذاتي، إعدادات الأذونات، خدمة Accessibility، وقفل Recent.
- لا تفعّل خاصية الدمج قبل توليد `.android/include_flutter.groovy` بواسطة Flutter.
- `AppGuardFlutterBridge` يستدعي `ProtectionRepository` الأصلي؛ لا توجد قاعدة بيانات ثانية في Dart.
- لم تُجرَ عملية build أو tests لهذه المرحلة لغياب Flutter SDK/تنزيلات Gradle في بيئة التنفيذ الحالية.
- قبل الاستخدام الإنتاجي، راجع توقيع الإصدار، سياسة رؤية التطبيقات، توافق Android API، وتأكد من اختبار كل استدعاء MethodChannel على جهاز فعلي.
