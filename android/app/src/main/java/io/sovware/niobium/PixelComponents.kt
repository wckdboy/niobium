package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.pixelPanel(radius: Dp = 18.dp, accented: Boolean = false): Modifier =
    this.clip(PixelButtonShape).background(Nb.Surface)
        .border(1.dp, if (accented) Nb.Ion.copy(alpha = 0.35f) else Nb.LineStrong, PixelButtonShape)
