package com.trama.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Trama atelier palette ─────────────────────────────────────────────────────
// Dark-first semantic tokens. Quiet graphite surfaces, soft ivory type and a
// small set of warm/cool accents. The goal is premium utility, not decoration.

// Neutrals — dark
val TramaBgDark = Color(0xFF08090A)
val TramaSurfDark = Color(0xFF101214)
val TramaSurf2Dark = Color(0xFF171A1C)
val TramaSurf3Dark = Color(0xFF202427)
val TramaTextDark = Color(0xFFF4F0E8)
val TramaMutedDark = Color(0xFF9A958C)
val TramaDimDark = Color(0xFF56524B)
val TramaBorderDark = Color(0x1AFFFFFF)
val TramaBorderDark2 = Color(0x0FFFFFFF)

// Neutrals — light (warm off-white to stay coherent with dark)
val TramaBgLight = Color(0xFFF7F4EE)
val TramaSurfLight = Color(0xFFFFFFFF)
val TramaSurf2Light = Color(0xFFF0ECE4)
val TramaSurf3Light = Color(0xFFE5DED2)
val TramaTextLight = Color(0xFF171614)
val TramaMutedLight = Color(0xFF6D665E)
val TramaDimLight = Color(0xFFAAA298)
val TramaBorderLight = Color(0x14000000)
val TramaBorderLight2 = Color(0x0A000000)

// Semantic accents (identical across themes for brand consistency)
val TramaAmber = Color(0xFFD48A52) // action / pending
val TramaTeal = Color(0xFF79B8A6)  // completed / chat / assistant
val TramaRed = Color(0xFFE06A5C)   // urgent / overdue / recording
val TramaWarn = Color(0xFFD2B45F)  // soft warn / due-today
val TramaWatch = Color(0xFF6EA1FF) // Wear OS / sync
val TramaInkBlue = Color(0xFF9AB7FF)

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
