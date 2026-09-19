import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  static const Color primary = Color(0xFF006591);
  static const Color onPrimary = Color(0xFFFFFFFF);
  static const Color primaryContainer = Color(0xFF0EA5E9);
  static const Color onPrimaryContainer = Color(0xFF003751);

  static const Color secondary = Color(0xFF4648D4);
  static const Color onSecondary = Color(0xFFFFFFFF);
  static const Color secondaryContainer = Color(0xFF6063EE);
  static const Color onSecondaryContainer = Color(0xFFFFFBFF);

  static const Color tertiary = Color(0xFF006C49);
  static const Color onTertiary = Color(0xFFFFFFFF);
  static const Color tertiaryContainer = Color(0xFF00B17B);
  static const Color onTertiaryContainer = Color(0xFF003B26);

  static const Color error = Color(0xFFBA1A1A);
  static const Color onError = Color(0xFFFFFFFF);
  static const Color errorContainer = Color(0xFFFFDAD6);
  static const Color onErrorContainer = Color(0xFF93000A);

  static const Color background = Color(0xFFFAF8FF);
  static const Color onBackground = Color(0xFF131B2E);
  static const Color surface = Color(0xFFFAF8FF);
  static const Color onSurface = Color(0xFF131B2E);
  static const Color surfaceContainer = Color(0xFFEAEDFF);
  static const Color surfaceContainerHigh = Color(0xFFE2E7FF);
  static const Color surfaceVariant = Color(0xFFDAE2FD);
  static const Color onSurfaceVariant = Color(0xFF3E4850);
  static const Color outline = Color(0xFF6E7881);
  static const Color outlineVariant = Color(0xFFBEC8D2);

  static final ColorScheme colorScheme = const ColorScheme.light(
    primary: primary,
    onPrimary: onPrimary,
    primaryContainer: primaryContainer,
    onPrimaryContainer: onPrimaryContainer,
    secondary: secondary,
    onSecondary: onSecondary,
    secondaryContainer: secondaryContainer,
    onSecondaryContainer: onSecondaryContainer,
    tertiary: tertiary,
    onTertiary: onTertiary,
    tertiaryContainer: tertiaryContainer,
    onTertiaryContainer: onTertiaryContainer,
    error: error,
    onError: onError,
    errorContainer: errorContainer,
    onErrorContainer: onErrorContainer,
    surface: surface,
    onSurface: onSurface,
    surfaceContainerHighest: surfaceVariant,
    onSurfaceVariant: onSurfaceVariant,
    outline: outline,
    outlineVariant: outlineVariant,
  );

  static ThemeData get lightTheme {
    return ThemeData(
      colorScheme: colorScheme,
      scaffoldBackgroundColor: Colors.transparent, // Background will be handled by mesh gradient
      useMaterial3: true,
      textTheme: TextTheme(
        displayLarge: GoogleFonts.outfit(fontSize: 32, fontWeight: FontWeight.w700, color: onSurface, letterSpacing: -0.64),
        headlineLarge: GoogleFonts.outfit(fontSize: 26, fontWeight: FontWeight.w600, color: onSurface, letterSpacing: -0.39),
        headlineMedium: GoogleFonts.outfit(fontSize: 22, fontWeight: FontWeight.w600, color: onSurface, letterSpacing: -0.22),
        headlineSmall: GoogleFonts.outfit(fontSize: 18, fontWeight: FontWeight.w600, color: onSurface, letterSpacing: -0.09),
        bodyLarge: GoogleFonts.outfit(fontSize: 16, fontWeight: FontWeight.w400, color: onSurface),
        bodyMedium: GoogleFonts.outfit(fontSize: 14, fontWeight: FontWeight.w400, color: onSurface),
        bodySmall: GoogleFonts.outfit(fontSize: 12, fontWeight: FontWeight.w400, color: onSurface, letterSpacing: 0.12),
        labelLarge: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: onSurface, letterSpacing: 0.26),
        labelMedium: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: onSurface, letterSpacing: 0.33),
        labelSmall: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w500, color: onSurface, letterSpacing: 0.6), // Used for label-mono
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
      ),
    );
  }

  static BoxDecoration glassDecoration = BoxDecoration(
    color: Colors.white.withValues(alpha: 0.68),
    borderRadius: BorderRadius.circular(16),
    border: Border.all(color: Colors.white.withValues(alpha: 0.7), width: 1.0),
    boxShadow: [
      BoxShadow(
        color: const Color(0xFF0F172A).withValues(alpha: 0.05),
        blurRadius: 24,
        offset: const Offset(0, 8),
      ),
      BoxShadow(
        color: const Color(0xFF0F172A).withValues(alpha: 0.02),
        blurRadius: 8,
        offset: const Offset(0, 2),
      ),
    ],
  );

  static BoxDecoration glassSubCardDecoration = BoxDecoration(
    color: Colors.white.withValues(alpha: 0.55),
    borderRadius: BorderRadius.circular(12),
    border: Border.all(color: Colors.white.withValues(alpha: 0.4), width: 1.0),
  );
}
