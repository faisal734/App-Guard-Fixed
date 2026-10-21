import 'package:flutter/material.dart';
import 'native_bridge.dart';

void main() => runApp(const AppGuardFlutterApp());

const _navy = Color(0xFF0B1220);
const _panel = Color(0xFF151F31);
const _panel2 = Color(0xFF1D2A40);
const _mint = Color(0xFF52E0B5);
const _muted = Color(0xFF9AA9BF);

class AppGuardFlutterApp extends StatelessWidget {
  const AppGuardFlutterApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'حارس التطبيقات',
        theme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          scaffoldBackgroundColor: _navy,
          colorScheme: ColorScheme.fromSeed(seedColor: _mint, brightness: Brightness.dark, surface: _panel),
          fontFamily: 'Roboto',
          appBarTheme: const AppBarTheme(backgroundColor: _navy, surfaceTintColor: Colors.transparent),
        ),
        home: const GuardShell(),
      );
}

class GuardShell extends StatefulWidget {
  const GuardShell({super.key});
  @override
  State<GuardShell> createState() => _GuardShellState();
}

class _GuardShellState extends State<GuardShell> {
  int _index = 0;
  bool _bridgeReady = false;
  bool _protection = true;
  @override
  void initState() {
    super.initState();
    _checkBridge();
  }
  Future<void> _checkBridge() async {
    final bridge = AppGuardNativeBridge.instance;
    final ready = await bridge.isAvailable;
    Map<String, dynamic>? status;
    if (ready) status = await bridge.getDashboardStatus();
    if (mounted) setState(() {
      _bridgeReady = ready;
      if (status != null && status['protectionEnabled'] is bool) {
        _protection = status['protectionEnabled'] as bool;
      }
    });
  }
  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      DashboardPage(bridgeReady: _bridgeReady, protection: _protection, onProtectionChanged: (v) async {
        if (_bridgeReady) {
          final ok = await AppGuardNativeBridge.instance.setProtectionEnabled(v);
          if (!ok && mounted) _notice('تعذر تحديث حالة الحماية من محرك Android');
          if (ok && mounted) setState(() => _protection = v);
        } else {
          _notice('واجهة Flutter غير متصلة بمحرك الحماية بعد');
        }
      }),
      AppsPage(bridgeReady: _bridgeReady, onNotice: _notice),
      ProfilesPage(bridgeReady: _bridgeReady, onNotice: _notice),
      SchedulesPage(bridgeReady: _bridgeReady, onNotice: _notice),
      SettingsPage(bridgeReady: _bridgeReady, onNotice: _notice),
    ];
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(
          title: const Row(children: [
            Icon(Icons.shield_moon_rounded, color: _mint, size: 29),
            SizedBox(width: 10),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('حارس التطبيقات', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
              Text('APP GUARD', style: TextStyle(fontSize: 10, letterSpacing: 2, color: _muted)),
            ]),
          ]),
          actions: [IconButton(onPressed: _checkBridge, icon: const Icon(Icons.sync_rounded), tooltip: 'فحص الاتصال')],
        ),
        body: AnimatedSwitcher(duration: const Duration(milliseconds: 220), child: pages[_index]),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _index,
          onDestinationSelected: (v) => setState(() => _index = v),
          backgroundColor: _panel,
          indicatorColor: _mint.withValues(alpha: .16),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.grid_view_rounded), label: 'الرئيسية'),
            NavigationDestination(icon: Icon(Icons.apps_rounded), label: 'التطبيقات'),
            NavigationDestination(icon: Icon(Icons.layers_rounded), label: 'الملفات'),
            NavigationDestination(icon: Icon(Icons.schedule_rounded), label: 'الجداول'),
            NavigationDestination(icon: Icon(Icons.tune_rounded), label: 'الإعدادات'),
          ],
        ),
      ),
    );
  }
  void _notice(String message) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}

class DashboardPage extends StatelessWidget {
  const DashboardPage({super.key, required this.bridgeReady, required this.protection, required this.onProtectionChanged});
  final bool bridgeReady;
  final bool protection;
  final ValueChanged<bool> onProtectionChanged;
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(18), children: [
    Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(gradient: const LinearGradient(begin: Alignment.topRight, end: Alignment.bottomLeft, colors: [Color(0xFF1D4B53), Color(0xFF17263A), _panel]), borderRadius: BorderRadius.circular(26), border: Border.all(color: _mint.withValues(alpha: .22))),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [const Expanded(child: Text('مركز الحماية', style: TextStyle(fontSize: 21, fontWeight: FontWeight.bold))), Icon(Icons.verified_user_rounded, size: 34, color: _mint)]),
        const SizedBox(height: 8), Text(bridgeReady ? 'محرك Android متصل' : 'واجهة التصميم — محرك الحماية غير متصل', style: const TextStyle(color: _muted, fontSize: 12)),
        const SizedBox(height: 20),
        Row(children: [Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(protection ? 'الحماية مفعّلة' : 'الحماية متوقفة', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700)), const SizedBox(height: 5), const Text('إدارة الحماية من مكان واحد', style: TextStyle(color: _muted, fontSize: 12))])), Switch(value: protection, activeColor: _mint, onChanged: onProtectionChanged)]),
      ]),
    ),
    const SizedBox(height: 18),
    const Row(children: [Expanded(child: MetricCard(icon: Icons.lock_outline_rounded, title: 'التطبيقات المقفلة', value: '—', caption: 'بانتظار ربط البيانات')), SizedBox(width: 12), Expanded(child: MetricCard(icon: Icons.layers_outlined, title: 'الملفات', value: '—', caption: 'بانتظار ربط البيانات'))]),
    const SizedBox(height: 12),
    const SectionTitle('الحماية المتقدمة'),
    const FeatureTile(icon: Icons.view_carousel_outlined, title: 'حماية التطبيقات الحديثة', subtitle: 'قفل شاشة Recent بعد انتهاء جلسة المصادقة'),
    const FeatureTile(icon: Icons.fingerprint_rounded, title: 'التحقق الآمن', subtitle: 'PIN وإمكانية دعم القياسات الحيوية'),
    const FeatureTile(icon: Icons.notifications_off_outlined, title: 'خزنة الإشعارات', subtitle: 'عرض الإشعارات المحجوبة عند تفعيل الميزة'),
    if (!bridgeReady) const Padding(padding: EdgeInsets.only(top: 12), child: InfoBanner(text: 'هذه المرحلة تنشئ واجهة Flutter فقط. لا تعتبر أزرارها حماية فعلية حتى يكتمل الربط مع Kotlin ويجتاز البناء والاختبارات.')),
  ]);
}

class AppsPage extends StatefulWidget {
  const AppsPage({super.key, required this.bridgeReady, required this.onNotice});
  final bool bridgeReady;
  final ValueChanged<String> onNotice;
  @override
  State<AppsPage> createState() => _AppsPageState();
}

class _AppsPageState extends State<AppsPage> {
  final _search = TextEditingController();
  List<Map<String, dynamic>> _apps = const [];
  bool _loading = false;
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _loadApps();
  }

  @override
  void didUpdateWidget(covariant AppsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.bridgeReady && !oldWidget.bridgeReady) _loadApps();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _loadApps() async {
    if (!widget.bridgeReady || _loading) return;
    setState(() => _loading = true);
    final bridge = AppGuardNativeBridge.instance;
    await bridge.refreshInstalledApps();
    final apps = await bridge.getInstalledApps();
    if (!mounted) return;
    setState(() {
      _apps = apps;
      _loading = false;
      _loaded = true;
    });
  }

  Future<void> _toggle(Map<String, dynamic> app, bool locked) async {
    final packageName = app['packageName'] as String?;
    if (packageName == null || packageName.isEmpty) {
      widget.onNotice('تعذر تحديد حزمة التطبيق');
      return;
    }
    final ok = await AppGuardNativeBridge.instance.setAppLocked(packageName, locked);
    if (!mounted) return;
    if (ok) {
      setState(() {
        final index = _apps.indexWhere((item) => item['packageName'] == packageName);
        if (index >= 0) _apps[index] = {..._apps[index], 'isLocked': locked};
      });
      widget.onNotice(locked ? 'تم تفعيل الحماية للتطبيق' : 'تم إلغاء حماية التطبيق');
    } else {
      widget.onNotice('لم يتأكد Android من حفظ التغيير؛ لم يتم تحديث الحالة محليًا');
    }
  }

  @override
  Widget build(BuildContext context) {
    final query = _search.text.trim().toLowerCase();
    final filtered = _apps.where((app) {
      final name = (app['appName'] ?? '').toString().toLowerCase();
      final pkg = (app['packageName'] ?? '').toString().toLowerCase();
      return name.contains(query) || pkg.contains(query);
    }).toList();
    return ListView(padding: const EdgeInsets.all(18), children: [
      const PageHeading(title: 'التطبيقات', subtitle: 'إدارة التطبيقات التي تريد حمايتها'),
      const SizedBox(height: 16),
      TextField(controller: _search, onChanged: (_) => setState(() {}), decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'ابحث عن تطبيق أو اسم الحزمة', filled: true, fillColor: _panel, border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(16)), borderSide: BorderSide.none))),
      const SizedBox(height: 12),
      if (!widget.bridgeReady) const InfoBanner(text: 'محرك Android غير متصل. لن تُعرض تطبيقات تجريبية ولن يُدّعى أن أي تطبيق محمي.'),
      if (widget.bridgeReady) Align(alignment: Alignment.centerLeft, child: IconButton(onPressed: _loadApps, icon: const Icon(Icons.refresh_rounded), tooltip: 'تحديث التطبيقات')),
      if (_loading) const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
      if (widget.bridgeReady && _loaded && _apps.isEmpty && !_loading) const InfoBanner(text: 'لم تُسترجع التطبيقات. تحقق من صلاحيات رؤية التطبيقات ومن اتصال محرك Android.'),
      if (widget.bridgeReady && !_loading && filtered.isEmpty && _apps.isNotEmpty) const Padding(padding: EdgeInsets.all(18), child: Center(child: Text('لا توجد نتائج مطابقة'))),
      ...filtered.map((app) {
        final name = (app['appName'] ?? 'تطبيق غير معروف').toString();
        final packageName = (app['packageName'] ?? '').toString();
        final locked = app['isLocked'] == true;
        final system = app['isSystemApp'] == true;
        return Card(color: _panel, child: SwitchListTile(
          value: locked,
          onChanged: widget.bridgeReady ? (value) => _toggle(app, value) : null,
          secondary: Container(width: 44, height: 44, decoration: BoxDecoration(color: _panel2, borderRadius: BorderRadius.circular(14)), child: Icon(system ? Icons.settings_suggest_rounded : Icons.apps_rounded, color: _mint)),
          title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)),
          subtitle: Text('$packageName\n${locked ? 'الحماية مفعّلة' : 'غير محمي'}', style: const TextStyle(color: _muted, fontSize: 11), maxLines: 2, overflow: TextOverflow.ellipsis),
          activeColor: _mint,
        ));
      }),
    ]);
  }
}

class ProfilesPage extends StatefulWidget {
  const ProfilesPage({super.key, required this.bridgeReady, required this.onNotice});
  final bool bridgeReady;
  final ValueChanged<String> onNotice;
  @override State<ProfilesPage> createState() => _ProfilesPageState();
}
class _ProfilesPageState extends State<ProfilesPage> {
  List<Map<String, dynamic>> _profiles = const [];
  bool _loading = false;
  @override void initState() { super.initState(); _load(); }
  @override void didUpdateWidget(covariant ProfilesPage oldWidget) { super.didUpdateWidget(oldWidget); if (widget.bridgeReady && !oldWidget.bridgeReady) _load(); }
  Future<void> _load() async {
    if (!widget.bridgeReady || _loading) return;
    setState(() => _loading = true);
    final rows = await AppGuardNativeBridge.instance.getProfiles();
    if (!mounted) return;
    setState(() { _profiles = rows; _loading = false; });
  }
  Future<void> _activate(Map<String, dynamic> profile) async {
    final id = (profile['id'] as num?)?.toInt();
    if (id == null) return;
    final ok = await AppGuardNativeBridge.instance.activateProfile(id);
    if (!mounted) return;
    if (ok) { await _load(); widget.onNotice('تم تفعيل ملف الحماية'); }
    else { widget.onNotice('تعذر تفعيل ملف الحماية'); }
  }
  Future<void> _deactivate() async {
    final ok = await AppGuardNativeBridge.instance.activateProfile(null);
    if (!mounted) return;
    if (ok) { await _load(); widget.onNotice('تم إلغاء ملف الحماية النشط'); }
    else { widget.onNotice('تعذر إلغاء الملف النشط'); }
  }
  @override Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(18), children: [
    const PageHeading(title: 'ملفات الحماية', subtitle: 'المجموعات المحفوظة في قاعدة بيانات Android'),
    const SizedBox(height: 12),
    if (!widget.bridgeReady) const InfoBanner(text: 'محرك Android غير متصل؛ لن تُعرض ملفات تجريبية.'),
    if (widget.bridgeReady) Align(alignment: Alignment.centerLeft, child: IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded), tooltip: 'تحديث الملفات')),
    if (_loading) const Center(child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator())),
    if (widget.bridgeReady && !_loading && _profiles.isEmpty) const InfoBanner(text: 'لا توجد ملفات حماية محفوظة في قاعدة البيانات.'),
    ..._profiles.map((p) { final active = p['isActive'] == true; final id = (p['id'] as num?)?.toInt(); return Card(color: _panel, child: ListTile(
      leading: Container(width: 44, height: 44, decoration: BoxDecoration(color: _panel2, borderRadius: BorderRadius.circular(14)), child: Center(child: Text((p['icon'] ?? '🛡').toString(), style: const TextStyle(fontSize: 22)))),
      title: Text((p['name'] ?? 'ملف').toString(), style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: Text(active ? 'نشط حاليًا' : ((p['isPreset'] == true) ? 'ملف افتراضي' : 'ملف مخصص'), style: const TextStyle(color: _muted)),
      trailing: active ? const Icon(Icons.check_circle_rounded, color: _mint) : FilledButton.tonal(onPressed: widget.bridgeReady && id != null ? () => _activate(p) : null, child: const Text('تفعيل')),
    )); }),
    if (widget.bridgeReady && _profiles.any((p) => p['isActive'] == true)) OutlinedButton.icon(onPressed: _deactivate, icon: const Icon(Icons.pause_circle_outline), label: const Text('إلغاء الملف النشط')),
  ]);
}

class SchedulesPage extends StatefulWidget {
  const SchedulesPage({super.key, required this.bridgeReady, required this.onNotice});
  final bool bridgeReady;
  final ValueChanged<String> onNotice;
  @override State<SchedulesPage> createState() => _SchedulesPageState();
}
class _SchedulesPageState extends State<SchedulesPage> {
  List<Map<String, dynamic>> _schedules = const [];
  bool _loading = false;
  @override void initState() { super.initState(); _load(); }
  @override void didUpdateWidget(covariant SchedulesPage oldWidget) { super.didUpdateWidget(oldWidget); if (widget.bridgeReady && !oldWidget.bridgeReady) _load(); }
  Future<void> _load() async {
    if (!widget.bridgeReady || _loading) return;
    setState(() => _loading = true);
    final rows = await AppGuardNativeBridge.instance.getSchedules();
    if (!mounted) return;
    setState(() { _schedules = rows; _loading = false; });
  }
  String _time(Map<String, dynamic> s, String prefix) {
    final h = ((s['${prefix}Hour'] as num?)?.toInt() ?? 0).toString().padLeft(2, '0');
    final m = ((s['${prefix}Minute'] as num?)?.toInt() ?? 0).toString().padLeft(2, '0');
    return '$h:$m';
  }
  Future<void> _toggle(Map<String, dynamic> s, bool value) async {
    final id = (s['id'] as num?)?.toInt();
    if (id == null) return;
    final ok = await AppGuardNativeBridge.instance.setScheduleEnabled(id, value);
    if (!mounted) return;
    if (ok) { await _load(); widget.onNotice(value ? 'تم تفعيل الجدول' : 'تم إيقاف الجدول'); }
    else { widget.onNotice('تعذر تحديث الجدول'); }
  }
  @override Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(18), children: [
    const PageHeading(title: 'الجداول الزمنية', subtitle: 'عرض الجداول المحفوظة وتفعيلها أو إيقافها'),
    const SizedBox(height: 12),
    if (!widget.bridgeReady) const InfoBanner(text: 'محرك Android غير متصل؛ لن تظهر جداول وهمية.'),
    if (widget.bridgeReady) Align(alignment: Alignment.centerLeft, child: IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded), tooltip: 'تحديث الجداول')),
    if (_loading) const Center(child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator())),
    if (widget.bridgeReady && !_loading && _schedules.isEmpty) const InfoBanner(text: 'لا توجد جداول محفوظة. إنشاء الجداول سيضاف بعد ربط واجهة التحرير بمحرك الجدولة.'),
    ..._schedules.map((s) { final enabled = s['isEnabled'] == true; final target = (s['targetType'] ?? 'حماية').toString().replaceAll('_', ' ').toLowerCase(); return Card(color: _panel, child: SwitchListTile(
      value: enabled, onChanged: widget.bridgeReady ? (v) => _toggle(s, v) : null, activeColor: _mint,
      secondary: const Icon(Icons.schedule_rounded, color: _mint),
      title: Text((s['name'] ?? 'جدول').toString(), style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: Text('${_time(s, 'start')} – ${_time(s, 'end')}  •  $target', style: const TextStyle(color: _muted, fontSize: 12)),
    )); }),
  ]);
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key, required this.bridgeReady, required this.onNotice});
  final bool bridgeReady;
  final ValueChanged<String> onNotice;
  @override State<SettingsPage> createState() => _SettingsPageState();
}
class _SettingsPageState extends State<SettingsPage> {
  bool _recentLock = true;
  bool _recentAlways = false;
  bool _loading = false;
  @override void initState() { super.initState(); _load(); }
  @override void didUpdateWidget(covariant SettingsPage oldWidget) { super.didUpdateWidget(oldWidget); if (widget.bridgeReady && !oldWidget.bridgeReady) _load(); }
  Future<void> _load() async {
    if (!widget.bridgeReady || _loading) return;
    setState(() => _loading = true);
    final status = await AppGuardNativeBridge.instance.getDashboardStatus();
    if (!mounted) return;
    setState(() { _recentLock = status?['recentAppsLockEnabled'] as bool? ?? true; _recentAlways = status?['recentAppsAlwaysLocked'] as bool? ?? false; _loading = false; });
  }
  Future<void> _setRecent(bool value, {required bool always}) async {
    final ok = always ? await AppGuardNativeBridge.instance.setRecentAppsAlwaysLocked(value) : await AppGuardNativeBridge.instance.setRecentAppsLockEnabled(value);
    if (!mounted) return;
    if (ok) { setState(() { if (always) _recentAlways = value; else _recentLock = value; }); widget.onNotice('تم حفظ الإعداد في Android'); }
    else { widget.onNotice('تعذر حفظ الإعداد؛ لم تتغير القيمة المعروضة'); }
  }
  @override Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(18), children: [
    const PageHeading(title: 'الإعدادات', subtitle: 'خيارات مرتبطة مباشرة بإعدادات الحماية الأصلية'),
    const SizedBox(height: 12),
    if (!widget.bridgeReady) const InfoBanner(text: 'محرك Android غير متصل. الإعدادات معطلة حتى لا تعرض الواجهة حالة غير حقيقية.'),
    if (_loading) const Center(child: Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator())),
    Card(color: _panel, child: SwitchListTile(value: _recentLock, onChanged: widget.bridgeReady ? (v) => _setRecent(v, always: false) : null, activeColor: _mint, secondary: const Icon(Icons.view_carousel_rounded, color: _mint), title: const Text('حماية التطبيقات الحديثة'), subtitle: const Text('قفل شاشة Recent بعد انتهاء جلسة المصادقة'))),
    Card(color: _panel, child: SwitchListTile(value: _recentAlways, onChanged: widget.bridgeReady && _recentLock ? (v) => _setRecent(v, always: true) : null, activeColor: _mint, secondary: const Icon(Icons.lock_clock_rounded, color: _mint), title: const Text('قفل Recent دائمًا'), subtitle: const Text('يتطلب المصادقة عند كل محاولة فتح'))),
    const FeatureTile(icon: Icons.password_rounded, title: 'رمز PIN', subtitle: 'إدارة الرمز ومهلة المحاولات تبقى في شاشة Android الأصلية'),
    const FeatureTile(icon: Icons.fingerprint_rounded, title: 'القياسات الحيوية', subtitle: 'لم تُنقل إلى Flutter لتجنب تكرار منطق التحقق الأمني'),
    const FeatureTile(icon: Icons.health_and_safety_outlined, title: 'التشخيص الذاتي', subtitle: 'فحص الأذونات وخدمات النظام — ربطه يأتي لاحقًا'),
  ]);
}

class MetricCard extends StatelessWidget { const MetricCard({super.key, required this.icon, required this.title, required this.value, required this.caption}); final IconData icon; final String title, value, caption; @override Widget build(BuildContext context) => Container(padding: const EdgeInsets.all(15), decoration: BoxDecoration(color: _panel, borderRadius: BorderRadius.circular(20), border: Border.all(color: Colors.white.withValues(alpha: .05))), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Icon(icon, color: _mint), const SizedBox(height: 12), Text(value, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)), Text(title, style: const TextStyle(fontWeight: FontWeight.w600)), const SizedBox(height: 4), Text(caption, style: const TextStyle(color: _muted, fontSize: 10))])); }
class PageHeading extends StatelessWidget { const PageHeading({super.key, required this.title, required this.subtitle}); final String title, subtitle; @override Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(title, style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w800)), const SizedBox(height: 5), Text(subtitle, style: const TextStyle(color: _muted))]); }
class SectionTitle extends StatelessWidget { const SectionTitle(this.title, {super.key}); final String title; @override Widget build(BuildContext context) => Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold))); }
class FeatureTile extends StatelessWidget { const FeatureTile({super.key, required this.icon, required this.title, required this.subtitle}); final IconData icon; final String title, subtitle; @override Widget build(BuildContext context) => Container(margin: const EdgeInsets.only(bottom: 9), decoration: BoxDecoration(color: _panel, borderRadius: BorderRadius.circular(17)), child: ListTile(contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5), leading: Container(width: 43, height: 43, decoration: BoxDecoration(color: _panel2, borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: _mint)), title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)), subtitle: Padding(padding: const EdgeInsets.only(top: 3), child: Text(subtitle, style: const TextStyle(color: _muted, fontSize: 12))), trailing: const Icon(Icons.chevron_left_rounded, color: _muted))); }
class InfoBanner extends StatelessWidget { const InfoBanner({super.key, required this.text}); final String text; @override Widget build(BuildContext context) => Container(padding: const EdgeInsets.all(13), decoration: BoxDecoration(color: const Color(0xFF2B3040), borderRadius: BorderRadius.circular(14), border: Border.all(color: Colors.amber.withValues(alpha: .25))), child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [const Icon(Icons.info_outline_rounded, color: Colors.amber, size: 20), const SizedBox(width: 9), Expanded(child: Text(text, style: const TextStyle(fontSize: 12, height: 1.5, color: Color(0xFFD3D9E4))))])); }
