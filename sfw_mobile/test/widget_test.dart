import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:sfw_mobile/main.dart';

void main() {
  testWidgets('App loads smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const SfwMobileApp());

    // Verify that our app starts.
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}