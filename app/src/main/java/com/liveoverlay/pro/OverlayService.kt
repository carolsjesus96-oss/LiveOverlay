package com.liveoverlay.pro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootView: FrameLayout
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "PRO_CHANNEL")
            .setContentTitle("LiveOverlay PRO")
            .setContentText("Sobreposição Gamer Ativa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("URL")
        val imageUriString = intent?.getStringExtra("IMAGE_URI")
        setupProOverlay(url, imageUriString)
        return START_NOT_STICKY
    }

    private fun setupProOverlay(url: String?, imageUriString: String?) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Root com moldura azul elétrica
        rootView = FrameLayout(this).apply {
            val padding = (2 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.parseColor("#007BFF")) // Moldura Azul Elétrico
        }

        val container = FrameLayout(this).apply {
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
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
            container.addView(webView)
        }

        rootView.addView(container)

        // Barra de Controle Superior (Transparente com handle e botões)
        val controlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC0F0F12"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                (32 * resources.displayMetrics.density).toInt(),
                Gravity.TOP
            )
        }

        val dragHandle = TextView(this).apply {
            text = "⠿"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }

        val btnWidthPlus = createIconButton(android.R.drawable.ic_input_add, "+L")
        val btnWidthMinus = createIconButton(android.R.drawable.ic_input_delete, "-L")
        val btnHeightPlus = createIconButton(android.R.drawable.ic_input_add, "+A")
        val btnHeightMinus = createIconButton(android.R.drawable.ic_input_delete, "-A")
        val btnClose = createIconButton(android.R.drawable.ic_menu_close_clear_cancel, "")

        controlBar.addView(btnWidthMinus)
        controlBar.addView(btnWidthPlus)
        controlBar.addView(dragHandle)
        controlBar.addView(btnHeightMinus)
        controlBar.addView(btnHeightPlus)
        controlBar.addView(btnClose)

        rootView.addView(controlBar)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            800,
            600,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(rootView, params)

        // Lógica de movimentação
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootView, params)
                    true
                }
                else -> false
            }
        }

        // Redimensionamento Independente
        btnWidthPlus.setOnClickListener {
            params.width = (params.width + 50)
            windowManager.updateViewLayout(rootView, params)
        }
        btnWidthMinus.setOnClickListener {
            params.width = (params.width - 50).coerceAtLeast(100)
            windowManager.updateViewLayout(rootView, params)
        }
        btnHeightPlus.setOnClickListener {
            params.height = (params.height + 50)
            windowManager.updateViewLayout(rootView, params)
        }
        btnHeightMinus.setOnClickListener {
            params.height = (params.height - 50).coerceAtLeast(100)
            windowManager.updateViewLayout(rootView, params)
        }

        btnClose.setOnClickListener { stopSelf() }
    }

    private fun createIconButton(resId: Int, label: String): View {
        if (label.isNotEmpty()) {
            return TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = Gravity.CENTER
                setBackgroundResource(android.R.drawable.btn_default)
                layoutParams = LinearLayout.LayoutParams(
                    (24 * resources.displayMetrics.density).toInt(),
                    (24 * resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins(2, 0, 2, 0)
                }
            }
        }
        return ImageView(this).apply {
            setImageResource(resId)
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            setColorFilter(Color.WHITE)
        }
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
                "LiveOverlay PRO Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
