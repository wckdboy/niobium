package io.sovware.niobium

import androidx.compose.ui.graphics.Color

val BotProfile.accent: Color get() = Nb.colorFor(accentHex ?: name)
