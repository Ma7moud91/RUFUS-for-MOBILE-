package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun ThemeCircularReveal(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Boolean) -> Unit
) {
    AnimatedContent(
        targetState = isDarkMode,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            // Keep the outgoing content while the incoming content reveals and fades in.
            // The incoming content will be drawn on top (higher z-index).
            (fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(700))).apply {
                targetContentZIndex = 1f
            }
        },
        label = "ThemeTransition"
    ) { dark ->
        val progress by transition.animateFloat(
            transitionSpec = { tween(700, easing = FastOutSlowInEasing) },
            label = "RevealProgress"
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> 0f
                EnterExitState.Visible -> 1f
                EnterExitState.PostExit -> 1f // keep the exiting state fully expanded so the new state reveals over it
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = CircularRevealShape(progress = progress)
                }
        ) {
            content(dark)
        }
    }
}
