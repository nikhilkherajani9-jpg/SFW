import 'package:flutter/material.dart';
import 'dart:ui';
import '../theme/app_theme.dart';

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final double borderRadius;
  final bool isSubCard;
  final bool hasBlur;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16.0),
    this.borderRadius = 16.0,
    this.isSubCard = false,
    this.hasBlur = true,
  });

  @override
  Widget build(BuildContext context) {
    final container = Container(
      padding: padding,
      decoration: isSubCard ? AppTheme.glassSubCardDecoration.copyWith(
        borderRadius: BorderRadius.circular(borderRadius)
      ) : AppTheme.glassDecoration.copyWith(
        borderRadius: BorderRadius.circular(borderRadius)
      ),
      child: child,
    );

    if (!hasBlur) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: container,
      );
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: isSubCard ? 12 : 16, sigmaY: isSubCard ? 12 : 16),
        child: container,
      ),
    );
  }
}
