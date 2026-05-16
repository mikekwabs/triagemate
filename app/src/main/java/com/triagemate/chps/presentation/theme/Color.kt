package com.triagemate.chps.presentation.theme

import androidx.compose.ui.graphics.Color

// ── Brand ──────────────────────────────────────────────────────────
val PrimaryNavy      = Color(0xFF00897B)
val PrimaryBlue      = PrimaryNavy           // alias used elsewhere
val LightBlueAccent  = Color(0xFFD9F2EE)

// ── Urgency traffic-light ──────────────────────────────────────────
val UrgencyRed       = Color(0xFFD32F2F)
val UrgencyAmber     = Color(0xFFF57C00)
val UrgencyGreen     = Color(0xFF388E3C)

// ── Symptom severity tiers ─────────────────────────────────────────
// Tier 1 — Critical (left accent bar + background)
val SeverityCriticalAccent = Color(0xFFE53935)   // deep red
val SeverityCriticalBg     = Color(0xFFFDE8E8)   // very light pink

// Tier 2 — Warning
val SeverityWarningAccent  = Color(0xFFF4A024)   // amber/orange
val SeverityWarningBg      = Color(0xFFFFF8E1)   // very light cream

// Tier 3 — General
val SeverityGeneralAccent  = PrimaryNavy         // brand teal
val SeverityGeneralBg      = Color(0xFFEAF9F7)  // very light teal

// ── Urgency result banners ─────────────────────────────────────────
val BannerRed    = Color(0xFFC62828)
val BannerAmber  = Color(0xFFE65100)
val BannerGreen  = Color(0xFF2E7D32)

// ── Agentic progress UI ────────────────────────────────────────────
val StepperTeal  = Color(0xFF00897B)   // stepper circles, connector lines, active text
val ProgressGreen = Color(0xFF43A047)  // completed step checkmark circles in progress card

// ── Default Material 3 seeds ───────────────────────────────────────
val Purple80     = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80       = Color(0xFFEFB8C8)
val Purple40     = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40       = Color(0xFF7D5260)
