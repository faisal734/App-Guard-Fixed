import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:app_guard_flutter/native_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.example.app_guard/protection');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      switch (call.method) {
        case 'isProtectionBridgeReady':
          return true;
        case 'getInstalledApps':
          return [
            {
              'packageName': 'com.example.mail',
              'appName': 'البريد',
              'isLocked': true,
              'isSystemApp': false,
            }
          ];
        case 'setAppLocked':
          return true;
        case 'setProtectionEnabled':
        case 'setRecentAppsLockEnabled':
        case 'setRecentAppsAlwaysLocked':
        case 'setScheduleEnabled':
        case 'activateProfile':
          return true;
        case 'getProfiles':
          return [
            {'id': 2, 'name': 'العمل', 'icon': '💼', 'isActive': true, 'isPreset': true}
          ];
        case 'getSchedules':
          return [
            {'id': 5, 'name': 'قفل الليل', 'startHour': 22, 'startMinute': 0, 'endHour': 7, 'endMinute': 0, 'isEnabled': true, 'targetType': 'GLOBAL_LOCK'}
          ];
        case 'getDashboardStatus':
          return {'protectionEnabled': true, 'recentAppsLockEnabled': true, 'recentAppsAlwaysLocked': false};
        case 'refreshInstalledApps':
          return true;
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('bridge reports ready and decodes app list', () async {
    final bridge = AppGuardNativeBridge.instance;
    expect(await bridge.isAvailable, isTrue);
    final apps = await bridge.getInstalledApps();
    expect(apps, hasLength(1));
    expect(apps.single['packageName'], 'com.example.mail');
    expect(apps.single['isLocked'], isTrue);
  });

  test('profiles, schedules, and Recent settings use the native contract', () async {
    final bridge = AppGuardNativeBridge.instance;
    expect((await bridge.getProfiles()).single['name'], 'العمل');
    expect((await bridge.getSchedules()).single['id'], 5);
    expect(await bridge.activateProfile(2), isTrue);
    expect(await bridge.setScheduleEnabled(5, false), isTrue);
    expect(await bridge.setRecentAppsLockEnabled(false), isTrue);
    expect(await bridge.setRecentAppsAlwaysLocked(true), isTrue);
    expect(calls.map((call) => call.method), containsAll([
      'getProfiles', 'getSchedules', 'activateProfile', 'setScheduleEnabled',
      'setRecentAppsLockEnabled', 'setRecentAppsAlwaysLocked',
    ]));
  });

  test('lock and refresh calls use the native contract', () async {
    final bridge = AppGuardNativeBridge.instance;
    expect(await bridge.setAppLocked('com.example.mail', true), isTrue);
    expect(await bridge.refreshInstalledApps(), isTrue);
    expect(calls.map((call) => call.method), containsAll(['setAppLocked', 'refreshInstalledApps']));
    final lockCall = calls.firstWhere((call) => call.method == 'setAppLocked');
    expect(lockCall.arguments, {'packageName': 'com.example.mail', 'locked': true});
  });
}
