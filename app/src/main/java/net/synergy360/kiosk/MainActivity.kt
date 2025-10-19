package net.synergy360.kiosk

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.*
import android.view.*
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private var sleepOverlay: View? = null
    private var offlineBanner: TextView? = null
    private var dimmed = false

    private val intervalMs = 120_000L
    private val handler = Handler(Looper.getMainLooper())
    private val cycle = object : Runnable {
        override fun run() {
            if (dimmed) wakeUp() else goSleep()
            handler.postDelayed(this, intervalMs)
        }
    }

    private val gestureWindowMs = 5_000L
    private val tappedCorners = mutableSetOf<Int>()
    private var gestureStartTs: Long = 0L

    companion object {
        private const val CORNER_TL = 1
        private const val CORNER_TR = 2
        private const val CORNER_BL = 3
        private const val CORNER_BR = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        root = FrameLayout(this)
        setContentView(root)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                showOffline("Reconnecting…")
            }
            override fun onPageFinished(view: WebView?, url: String?) { hideOffline() }
        }
        webView.loadUrl("https://app.360synergy.net")
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val touchLayer = object : View(this) {
            override fun onTouchEvent(e: MotionEvent?): Boolean {
                if (e?.action == MotionEvent.ACTION_DOWN) handleCornerTap(e.x, e.y)
                return false
            }
        }
        root.addView(touchLayer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        handler.postDelayed(cycle, intervalMs)
        try { startLockTask() } catch (_: Exception) {}
    }

    private fun showOffline(text: String) {
        if (offlineBanner == null) {
            offlineBanner = TextView(this).apply {
                setBackgroundColor(0xCC000000.toInt())
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(32, 24, 32, 24)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.TOP
            root.addView(offlineBanner, lp)
        }
        offlineBanner?.text = text
        offlineBanner?.visibility = View.VISIBLE
    }

    private fun hideOffline() { offlineBanner?.visibility = View.GONE }

    private fun goSleep() {
        if (sleepOverlay == null) {
            sleepOverlay = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 1f }
            root.addView(sleepOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        dimmed = true
    }

    private fun wakeUp() {
        sleepOverlay?.let {
            root.removeView(it)
            sleepOverlay = null
        }
        webView.onResume()
        webView.reload()
        dimmed = false
    }

    private fun handleCornerTap(x: Float, y: Float) {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val margin = (w.coerceAtMost(h) * 0.15f)

        val corner = when {
            x < margin && y < margin -> CORNER_TL
            x > w - margin && y < margin -> CORNER_TR
            x < margin && y > h - margin -> CORNER_BL
            x > w - margin && y > h - margin -> CORNER_BR
            else -> 0
        }
        if (corner == 0) return

        val now = SystemClock.uptimeMillis()
        if (gestureStartTs == 0L || now - gestureStartTs > gestureWindowMs) {
            tappedCorners.clear()
            gestureStartTs = now
        }
        tappedCorners.add(corner)

        if (tappedCorners.size == 4 && now - gestureStartTs <= gestureWindowMs) {
            tappedCorners.clear()
            gestureStartTs = 0L
            confirmExit()
        }
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Exit kiosk mode?")
            .setMessage("Close the app and leave fullscreen.")
            .setPositiveButton("Yes") { _, _ ->
                try { stopLockTask() } catch (_: Exception) {}
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(cycle)
    }
}
