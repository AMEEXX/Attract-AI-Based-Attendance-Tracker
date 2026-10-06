package com.attract.attendance.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- Brand seed colors — never reference these directly in a screen ----------
val SeedIndigo = Color(0xFF4F46E5)
val SeedEmerald = Color(0xFF10B981)
val SeedCoral = Color(0xFFFB6F5C)
val SeedAmber = Color(0xFFF5A524)
val SeedCrimson = Color(0xFFE5484D)

// Legacy aliases for backward compatibility
val AttractBlue = SeedIndigo
val AttractDeepBlue = Color(0xFF2E2A8F)
val AttractSoftBlue = Color(0xFFD9D6FB)
val AttractBlueContainer = Color(0xFFE7E5FC)
val SuccessGreen = SeedEmerald
val WarningAmber = SeedAmber
val ErrorRed = SeedCrimson
val NeutralGray = Color(0xFF6E6E7C)

// Biometric Dark World aliases
val BiometricBackground = Color(0xFF000000)
val BiometricSurface = Color(0xFF1C1C26)
val BiometricTextPrimary = Color(0xFFF4F4F8)
val BiometricIndigo = Color(0xFF8B85F5)
val BiometricSuccess = Color(0xFF34E0A1)
val BiometricWarning = Color(0xFFFFB84D)
val BiometricError = Color(0xFFFF6B6E)

// ---------- Light theme tokens ----------
object LightColors {
    val background = Color(0xFFFAFAFB)
    val surface = Color(0xFFFFFFFF)
    val surfaceRaised = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF2F2F7)
    val onBackground = Color(0xFF14141B)
    val onSurface = Color(0xFF14141B)
    val onSurfaceVariant = Color(0xFF6E6E7C)
    val outline = Color(0xFFE7E7EF)

    val primary = Color(0xFF4F46E5)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFE7E5FC)
    val onPrimaryContainer = Color(0xFF2E2A8F)

    val success = Color(0xFF10B981)
    val onSuccess = Color(0xFFFFFFFF)
    val successContainer = Color(0xFFDAF6EB)
    val onSuccessContainer = Color(0xFF0B5136)

    val warning = Color(0xFFF5A524)
    val onWarning = Color(0xFF3D2600)
    val warningContainer = Color(0xFFFCEACB)
    val onWarningContainer = Color(0xFF5C3D00)

    val error = Color(0xFFE5484D)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFBDCDC)
    val onErrorContainer = Color(0xFF7A1315)

    val coral = Color(0xFFFB6F5C)
    val onCoral = Color(0xFFFFFFFF)
}

// ---------- Dark theme tokens ----------
object DarkColors {
    val background = Color(0xFF0A0A0E)
    val surface = Color(0xFF14141B)
    val surfaceRaised = Color(0xFF1C1C26)
    val surfaceVariant = Color(0xFF1F1F29)
    val onBackground = Color(0xFFF4F4F8)
    val onSurface = Color(0xFFF4F4F8)
    val onSurfaceVariant = Color(0xFF9C9CAC)
    val outline = Color(0xFF2A2A38)

    val primary = Color(0xFF8B85F5)
    val onPrimary = Color(0xFF14102E)
    val primaryContainer = Color(0xFF2A2470)
    val onPrimaryContainer = Color(0xFFD9D6FB)

    val success = Color(0xFF34E0A1)
    val onSuccess = Color(0xFF06301F)
    val successContainer = Color(0xFF113328)
    val onSuccessContainer = Color(0xFF9CF2D3)

    val warning = Color(0xFFFFB84D)
    val onWarning = Color(0xFF3D2600)
    val warningContainer = Color(0xFF3D2E0F)
    val onWarningContainer = Color(0xFFFFDDA8)

    val error = Color(0xFFFF6B6E)
    val onError = Color(0xFF3D0A0B)
    val errorContainer = Color(0xFF3D1315)
    val onErrorContainer = Color(0xFFFFC9CA)

    val coral = Color(0xFFFF8B79)
    val onCoral = Color(0xFF3D1006)
}

// ---------- Dedicated Camera Screen palette ----------
object CameraScreenColors {
    val background = Color(0xFF000000) // pure black — frozen
    val onBackground = Color(0xFFF4F4F8)
    val surfaceRaised = Color(0xFF1C1C26)

    val glowIndigo = Color(0xFF8B85F5)
    val glowEmerald = Color(0xFF34E0A1)
    val glowAmber = Color(0xFFFFB84D)
    val glowCrimson = Color(0xFFFF6B6E)
}
