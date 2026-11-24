package com.getmobilestack.kmp.pintar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getmobilestack.kmp.pintar.learning.VideoPlayback

/** Displays a native media player or a sandboxed YouTube embed; never receives auth tokens. */
@Composable
expect fun LearningVideoPlayer(playback: VideoPlayback, modifier: Modifier = Modifier, onEnded: () -> Unit)
