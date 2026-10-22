package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.pixelPanel(radius: Dp = 18.dp, accented: Boolean = false): Modifier =
    this.background(Nb.Surface.copy(alpha = 0.94f), RoundedCornerShape(radius))
        .border(1.dp, if (accented) Nb.Ion.copy(alpha = 0.24f) else Nb.LineStrong.copy(alpha = Nb.LineStrong.alpha * 0.72f), RoundedCornerShape(radius))
        .drawWithContent {
            drawContent()
            val cell = 3.dp.toPx()
            val tint = Nb.Ion.copy(alpha = 0.28f)
            drawRect(tint, Offset(10.dp.toPx(), 15.dp.toPx()), Size(cell, 7.dp.toPx()))
            drawRect(tint, Offset(13.dp.toPx(), 12.dp.toPx()), Size(7.dp.toPx(), cell))
            drawRect(tint, Offset(21.dp.toPx(), 9.dp.toPx()), Size(cell, cell))
        }
