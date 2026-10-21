import 'package:flutter_test/flutter_test.dart';
import 'package:app_guard_flutter/main.dart';
import 'package:flutter/material.dart';

void main() {
  testWidgets('renders Arabic App Guard dashboard', (tester) async {
    await tester.pumpWidget(const AppGuardFlutterApp());
    expect(find.text('حارس التطبيقات'), findsOneWidget);
    expect(find.text('مركز الحماية'), findsOneWidget);
    expect(find.text('التطبيقات'), findsWidgets);
  });
}
