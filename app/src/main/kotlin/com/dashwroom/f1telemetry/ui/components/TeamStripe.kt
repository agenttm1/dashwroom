package com.dashwroom.f1telemetry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The thin team-colour bar beside a driver code. */
@Composable
fun TeamStripe(color: Color, modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Box(modifier.width(4.dp).height(height).background(color, RoundedCornerShape(2.dp)))
}
