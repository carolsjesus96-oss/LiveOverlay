package com.liveoverlay.pro

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.atan2

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "PRO_CHANNEL")
            .setContentTitle("LiveOverlay PRO Multi")
            .setContentText("Múltiplas instâncias ativas")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("URL")
        val imageUriString = intent?.getStringExtra("IMAGE_URI")
        
        createOverlayWindow(url, imageUriString)
        
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayWindow(url: String?, imageUriString: String?) {
        val context = this
        val baseSize = 800f // Tamanho base da lógica
        
        // 1. Root da Janela
        val rootView = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        // 2. Container de Conteúdo
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(baseSize.toInt(), baseSize.toInt())
            pivotX = baseSize / 2f
            pivotY = baseSize / 2f
        }
        
        if (!imageUriString.isNullOrEmpty()) {
            val imageView = ImageView(context).apply {
                setImageURI(Uri.parse(imageUriString))
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }
            container.addView(imageView)
        } else if (!url.isNullOrEmpty()) {
            val webView = WebView(context).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
            container.addView(webView)
        }
        rootView.addView(container)

        // 3. Glass Pane (Interceptor)
        val glassPane = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(baseSize.toInt(), baseSize.toInt())
            pivotX = baseSize / 2f
            pivotY = baseSize / 2f
        }
        rootView.addView(glassPane)

        // 4. Botão Fechar (X)
        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 20f
            setShadowLayer(6f, 3f, 3f, Color.BLACK)
            visibility = View.GONE
            val size = (44 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END)
            gravity = Gravity.CENTER
            translationZ = 100f // Sempre no topo
        }
        rootView.addView(btnClose)

        // 5. Configuração LayoutParams (Janela Física)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            baseSize.toInt(),
            baseSize.toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 200 + (overlayViews.size * 50)
        params.y = 200 + (overlayViews.size * 50)

        windowManager.addView(rootView, params)
        overlayViews.add(rootView)

        btnClose.setOnClickListener {
            findWebView(container)?.let { 
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy() 
            }
            windowManager.removeView(rootView)
            overlayViews.remove(rootView)
            if (overlayViews.isEmpty()) {
                stopSelf()
            }
        }

        setupGestures(glassPane, rootView, container, btnClose, params, baseSize)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findWebView(child)
                if (result != null) return result
            }
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures(
        view: View, 
        rootView: View, 
        container: View, 
        btnClose: TextView, 
        params: WindowManager.LayoutParams,
        baseSize: Float
    ) {
        var rotationAngle = 0f
        var totalScale = 1.0f
        
        fun updateWindowBoundingBox() {
            val matrix = Matrix()
            matrix.postScale(totalScale, totalScale, baseSize / 2f, baseSize / 2f)
            matrix.postRotate(rotationAngle, baseSize / 2f, baseSize / 2f)
            
            val rect = RectF(0f, 0f, baseSize, baseSize)
            matrix.mapRect(rect)
            
            // Ajusta a Janela Física para a nova Bounding Box (Fim do Corte)
            params.width = rect.width().toInt()
            params.height = rect.height().toInt()
            
            // Centraliza o conteúdo dentro da janela expandida
            container.translationX = (params.width - baseSize) / 2f
            container.translationY = (params.height - baseSize) / 2f
            view.translationX = container.translationX
            view.translationY = container.translationY
            
            windowManager.updateViewLayout(rootView, params)
        }

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                totalScale *= detector.scaleFactor
                totalScale = totalScale.coerceIn(0.1f, 5.0f)
                
                container.scaleX = totalScale
                container.scaleY = totalScale
                view.scaleX = totalScale
                view.scaleY = totalScale
                
                updateWindowBoundingBox()
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                btnClose.visibility = if (btnClose.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                return true
            }
        })

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastRotation = 0f

        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            
            val pointerCount = event.pointerCount

            if (pointerCount == 1) {
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
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        lastRotation = rotationAngle(event)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val currentRotation = rotationAngle(event)
                        val delta = currentRotation - lastRotation
                        rotationAngle += delta
                        container.rotation = rotationAngle
                        view.rotation = rotationAngle
                        
                        updateWindowBoundingBox()
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
        for (view in overlayViews) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
        }
        overlayViews.clear()
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
