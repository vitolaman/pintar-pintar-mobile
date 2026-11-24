package com.getmobilestack.kmp.pintar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.interop.UIKitViewController
import com.getmobilestack.kmp.pintar.learning.VideoPlayback
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.Foundation.*
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.darwin.NSObject
import kotlinx.cinterop.ExperimentalForeignApi

private class YouTubeNavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (platform.WebKit.WKNavigationActionPolicy) -> Unit) {
        val action = decidePolicyForNavigationAction
        if (action.request.URL?.absoluteString == "about:blank") { decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow); return }
        if (action.targetFrame?.isMainFrame() == false) { decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow); return }
        val url = action.request.URL
        val host = url?.host?.lowercase()
        val allowed = url?.scheme == "https" && host in setOf("www.youtube.com", "youtube.com", "m.youtube.com", "www.youtube-nocookie.com")
        decisionHandler(if (allowed) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LearningVideoPlayer(playback: VideoPlayback, modifier: Modifier, onEnded: () -> Unit) {
    val ended = rememberUpdatedState(onEnded)
    when (playback) {
        is VideoPlayback.File -> {
            val nativePlayer: AVPlayer? = remember(playback.url) { NSURL.URLWithString(playback.url)?.let { AVPlayer.playerWithURL(it) } }
            val controller = remember(playback.url) {
                AVPlayerViewController().apply { player = nativePlayer }
            }
            DisposableEffect(controller) {
                if (nativePlayer == null) return@DisposableEffect onDispose { }
                val item = nativePlayer?.currentItem
                val observer = item?.let { NSNotificationCenter.defaultCenter.addObserverForName(
                    AVPlayerItemDidPlayToEndTimeNotification, it, NSOperationQueue.mainQueue
                ) { ended.value() } }
                val backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    platform.UIKit.UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue
                ) { nativePlayer.pause() }
                onDispose { observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }; NSNotificationCenter.defaultCenter.removeObserver(backgroundObserver); nativePlayer.pause(); controller.player = null }
            }
            UIKitViewController(factory = { controller }, modifier = modifier, onRelease = { nativePlayer?.pause() })
        }
        is VideoPlayback.YouTube -> {
            val delegate = remember(playback.id) { YouTubeNavigationDelegate() }
            val web = remember(playback.id) {
                WKWebView(frame = kotlinx.cinterop.cValue { }, configuration = WKWebViewConfiguration().apply { allowsInlineMediaPlayback = true }).apply {
                    navigationDelegate = delegate
                    NSURL.URLWithString("https://www.youtube.com/embed/${playback.id}?playsinline=1")?.let {
                        val request = NSMutableURLRequest.requestWithURL(it) as NSMutableURLRequest
                        request.setValue("https://pintarpintar.id/", forHTTPHeaderField = "Referer")
                        loadRequest(request)
                    }
                }
            }
            DisposableEffect(web, delegate) {
                val backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    platform.UIKit.UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue
                ) { web.stopLoading(); web.loadHTMLString("", baseURL = null) }
                val activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    platform.UIKit.UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue
                ) {
                    NSURL.URLWithString("https://www.youtube.com/embed/${playback.id}?playsinline=1")?.let {
                        val request = NSMutableURLRequest.requestWithURL(it) as NSMutableURLRequest
                        request.setValue("https://pintarpintar.id/", forHTTPHeaderField = "Referer")
                        web.loadRequest(request)
                    }
                }
                onDispose { NSNotificationCenter.defaultCenter.removeObserver(backgroundObserver); NSNotificationCenter.defaultCenter.removeObserver(activeObserver); web.stopLoading(); web.navigationDelegate = null; web.loadHTMLString("", baseURL = null) }
            }
            UIKitView(factory = { web }, modifier = modifier, onRelease = { it.stopLoading(); it.loadHTMLString("", baseURL = null) })
        }
        is VideoPlayback.External -> Unit
    }
}
