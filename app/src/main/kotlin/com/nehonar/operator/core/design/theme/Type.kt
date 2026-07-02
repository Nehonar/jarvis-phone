package com.nehonar.operator.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Único punto de cambio tipográfico: sustituir por JetBrains Mono (res/font)
// cuando se añadan los TTF; ver docs/decisiones.md D-003.
val OperatorFontFamily: FontFamily = FontFamily.Monospace

val OperatorTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = OperatorFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = OperatorFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 1.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = OperatorFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = OperatorFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = OperatorFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
    ),
)
