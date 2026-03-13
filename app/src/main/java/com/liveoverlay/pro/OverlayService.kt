package com.liveoverlay.pro

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.atan2
import kotlin.math.toDegrees

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootView: FrameLayout
    private lateinit var container: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    
    // Gesture state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    private var scaleFactor = 1.0f
    private var rotationAngle = 0f
    
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "PRO_CHANNEL")
            .setContentTitle("LiveOverlay Steath")
            .setContentText("Controle por gestos ativo")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("URL")
        val imageUriString = intent?.getStringExtra("IMAGE_URI")
        setupStealthOverlay(url, imageUriString)
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStealthOverlay(url: String?, imageUriString: String?) {
        // Root invisível
        rootView = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // WebView ou ImageView
        if (!imageUriString.isNullOrEmpty()) {
            val imageView = ImageView(this).apply {
                setImageURI(Uri.parse(imageUriString))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            container.addView(imageView)
        } else if (!url.isNullOrEmpty()) {
            val webView = WebView(this).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webViewClient = WebViewClient()
                loadUrl(url)
            }
            container.addView(webView)
        }

        rootView.addView(container)

        // Glass Pane - Interceptor de toques
        val glassPane = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootView.addView(glassPane)

        // Configuração inicial da janela
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            600, 600, // Tamanho base
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 200
        params.y = 200

        windowManager.addView(rootView, params)

        // Detectores de Gestos
        setupGestures(glassPane)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures(view: View) {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.1f, 5.0f)
                container.scaleX = scaleFactor
                container.scaleY = scaleFactor
                return true
            }
        })

        var lastRotation = 0f

        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            
            val pointerCount = event.pointerCount

            if (pointerCount == 1) {
                // Arrastar
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(rootView, params)
                    }
                }
            } else if (pointerCount == 2) {
                // Rotação
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        lastRotation = rotationAngle(event)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val currentRotation = rotationAngle(event)
                        val delta = currentRotation - lastRotation
                        rotationAngle += delta
                        container.rotation = rotationAngle
                        lastRotation = currentRotation
                    }
                }
            }
            true
        }
    }

    private fun rotationAngle(event: MotionEvent): Float {
        val dx = (event.getX(0) - event.getX(1)).toDouble()
        val dy = (event.getY(0) - event.getY(1)).toDouble()
        val radians = atan2(dy, dx)
        return Math.toDegrees(radians).toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::rootView.isInitialized) {
            windowManager.removeView(rootView)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "PRO_CHANNEL",
                "LiveOverlay Stealth",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
