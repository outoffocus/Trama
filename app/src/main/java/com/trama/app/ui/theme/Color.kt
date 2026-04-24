package com.trama.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Trama Redesign v2 palette ─────────────────────────────────────────────────
// Dark-first semantic tokens. 5 accents: amber (action), teal (completed/chat),
// red (urgent), warn (overdue soft), watch (Wear sync).

// Neutrals — dark
val TramaBgDark = Color(0xFF0B0B0D)
val TramaSurfDark = Color(0xFF141416)
val TramaSurf2Dark = Color(0xFF1C1C1F)
val TramaSurf3Dark = Color(0xFF252527)
val TramaTextDark = Color(0xFFF0EFE8)
val TramaMutedDark = Color(0xFF6E6D68)
val TramaDimDark = Color(0xFF3A3A3D)
val TramaBorderDark = Color(0x12FFFFFF) // rgba(255,255,255,0.07)
val TramaBorderDark2 = Color(0x0AFFFFFF) // rgba(255,255,255,0.04)

// Neutrals — light (warm off-white to stay coherent with dark)
val TramaBgLight = Color(0xFFF6F3EC)
val TramaSurfLight = Color(0xFFFFFFFF)
val TramaSurf2Light = Color(0xFFEFEBE2)
val TramaSurf3Light = Color(0xFFE5E0D3)
val TramaTextLight = Color(0xFF1A1A1C)
val TramaMutedLight = Color(0xFF6B6A65)
val TramaDimLight = Color(0xFFB8B6AE)
val TramaBorderLight = Color(0x14000000)
val TramaBorderLight2 = Color(0x0A000000)

// Semantic accents (identical across themes for brand consistency)
val TramaAmber = Color(0xFFC8753A) // action / pending
val TramaTeal = Color(0xFF4A9D8F)  // completed / chat / assistant
val TramaRed = Color(0xFFD45A4A)   // urgent / overdue / recording
val TramaWarn = Color(0xFFC8A43A)  // soft warn / due-today
val TramaWatch = Color(0xFF5588EE) // Wear OS / sync

// Legacy tokens kept for callers still referencing them (Search/Category accents).
// Values kept close to new palette to avoid jarring mix when rendered together.
val Purple80 = Color(0xFFB8D8E8)
val PurpleGrey80 = Color(0xFFC8D3D5)
val Pink80 = Color(0xFFF6C9AE)
val Purple40 = Color(0xFF1E5F74)
val PurpleGrey40 = Color(0xFF4D6C73)
val Pink40 = Color(0xFFCC7A42)

// Accent palette (legacy — kept for dynamic category IDs)
val Coral = TramaRed
val Amber = TramaAmber
val Emerald = TramaTeal
val SkyBlue = TramaWatch
val Lavender = Color(0xFFA29BFE)
val Peach = TramaWarn
val Mint = TramaTeal
val Rose = TramaRed

// Surface accents (legacy)
val SurfaceWarm = TramaSurfLight
val SurfaceCool = TramaSurfLight
val SurfaceMint = TramaSurfLight
val SurfaceWarmDark = TramaSurf2Dark
val SurfaceCoolDark = TramaSurf2Dark
val SurfaceMintDark = TramaSurf2Dark

// Reduced to the 5 Trama semantic colors (cycled for any LLM category id).
val CategoryColors = listOf(
    TramaAmber,
    TramaTeal,
    TramaRed,
    TramaWarn,
    TramaWatch,
)
