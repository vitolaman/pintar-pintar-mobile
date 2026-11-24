package com.getmobilestack.kmp.pintar.ui

import android.graphics.Color
import android.view.Gravity
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.MediaController
import android.widget.VideoView
import android.widget.TextView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.getmobilestack.kmp.pintar.learning.VideoPlayback

@Composable
actual fun LearningVideoPlayer(playback: VideoPlayback, modifier: Modifier, onEnded: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ended = rememberUpdatedState(onEnded)
    when (playback) {
        is VideoPlayback.File -> {
            val video = remember(playback.url) {
                VideoView(context).apply {
                    setVideoURI(Uri.parse(playback.url))
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnCompletionListener { ended.value() }
                }
            }
            val container = remember(video) {
                FrameLayout(context).apply {
                    setBackgroundColor(Color.BLACK)
                    addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    val notice = TextView(context).apply {
                        setTextColor(Color.WHITE); gravity = Gravity.CENTER
                        text = "Video belum dapat diputar. Tutup lalu coba lagi."
                        visibility = android.view.View.GONE
                    }
                    addView(notice, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    video.setOnErrorListener { _, _, _ -> notice.visibility = android.view.View.VISIBLE; true }
                }
            }
            DisposableEffect(video, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) video.pause() }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer); video.stopPlayback() }
            }
            AndroidView(factory = { container }, modifier = modifier)
        }
        is VideoPlayback.YouTube -> {
            val web = remember(playback.id) {
                WebView(context).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            if (request?.isForMainFrame != true) return false
                            val uri = request.url
                            val host = uri.host?.lowercase()
                            return uri.scheme != "https" || host !in setOf("www.youtube.com", "youtube.com", "m.youtube.com", "www.youtube-nocookie.com")
                        }
                    }
                    webChromeClient = WebChromeClient()
                    loadUrl("https://www.youtube.com/embed/${playback.id}?playsinline=1", mapOf("Referer" to "https://pintarpintar.id/"))
                }
            }
            DisposableEffect(web, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) web.onPause() else if (event == Lifecycle.Event.ON_RESUME) web.onResume()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer); (web.parent as? ViewGroup)?.removeView(web); web.stopLoading(); web.destroy() }
            }
            AndroidView(factory = { web }, modifier = modifier)
        }
        is VideoPlayback.External -> Unit
    }
}
