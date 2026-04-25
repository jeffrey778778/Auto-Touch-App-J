package com.zenops.precisiontap

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Foreground Service:
 *   1. 다른 앱 위 플로팅 카운트다운 UI
 *   2. ms 정밀 트리거 — sleep + spin-wait 하이브리드
 *   3. 트리거 시 AccessibilityService.performTap() 호출
 */
class OverlayService : Service() {

    companion object {
        const val EXTRA_TARGET_MILLIS = "targetMillis"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_REPEAT = "repeat"
        const val EXTRA_INTERVAL = "interval"

        private const val CHANNEL_ID = "precision_tap_channel"
        private const val NOTI_ID = 1
    }

    private var overlayView: View? = null
    private var wm: WindowManager? = null
    private var triggerThread: Thread? = null
    @Volatile private var cancelled = false

    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        val targetMillis = intent.getLongExtra(EXTRA_TARGET_MILLIS, 0L)
        val x = intent.getFloatExtra(EXTRA_X, 0f)
        val y = intent.getFloatExtra(EXTRA_Y, 0f)
        val repeat = intent.getIntExtra(EXTRA_REPEAT, 1)
        val interval = intent.getLongExtra(EXTRA_INTERVAL, 0L)

        startForeground(NOTI_ID, buildNotification(targetMillis))
        showOverlay(targetMillis)
        scheduleTrigger(targetMillis, x, y, repeat, interval)

        return START_NOT_STICKY
    }

    // ===================== 정밀 트리거 =====================
    private fun scheduleTrigger(
        targetMillis: Long, x: Float, y: Float,
        repeat: Int, interval: Long
    ) {
        cancelled = false
        triggerThread = thread(name = "PrecisionTapTrigger", isDaemon = true) {
            // 1단계: sleep — 최종 8ms 전까지
            while (!cancelled) {
                val remaining = targetMillis - System.currentTimeMillis()
                if (remaining <= 8L) break
                try {
                    // 50ms 단위로 sleep 하면서 cancel 체크
                    Thread.sleep(minOf(remaining - 8L, 50L))
                } catch (_: InterruptedException) { return@thread }
            }
            if (cancelled) return@thread

            // 2단계: spin-wait — 마지막 ~8ms (CPU 점유 짧으므로 OK)
            // System.currentTimeMillis() 자체 해상도가 보통 1ms
            while (!cancelled && System.currentTimeMillis() < targetMillis) {
                // tight loop, no yield (정밀도 우선)
            }
            if (cancelled) return@thread

            // 3단계: 트리거 — 첫 탭
            val svc = AutoTapAccessibilityService.instance
            if (svc == null) {
                ui.post { updateOverlayText("ERROR: 접근성 서비스 OFF") }
                return@thread
            }
            svc.performTap(x, y)

            // 반복 탭 (옵션)
            for (i in 1 until repeat) {
                if (cancelled) return@thread
                if (interval > 0) {
                    try { Thread.sleep(interval) } catch (_: InterruptedException) { return@thread }
                }
                svc.performTap(x, y)
            }

            ui.post {
                updateOverlayText("DONE @ ${SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA)
                    .format(Date())}")
            }
        }
    }

    // ===================== 플로팅 오버레이 =====================
    private fun showOverlay(targetMillis: Long) {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 200
        }

        val v = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        overlayView = v
        val tv = v.findViewById<TextView>(R.id.tvCountdown)
        v.findViewById<Button>(R.id.btnCancel).setOnClickListener { stopSelf() }

        // 드래그로 이동
        v.setOnTouchListener(object : View.OnTouchListener {
            var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(view: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = lp.x; initY = lp.y
                        touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = initX + (e.rawX - touchX).toInt()
                        lp.y = initY + (e.rawY - touchY).toInt()
                        wm?.updateViewLayout(v, lp)
                    }
                }
                return false
            }
        })

        wm?.addView(v, lp)

        // 카운트다운 갱신 — 100ms 주기
        val tick = object : Runnable {
            override fun run() {
                if (cancelled) return
                val remaining = targetMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    tv.text = "FIRING…"
                } else {
                    val totalMs = remaining
                    val h = (totalMs / 3600_000).toInt()
                    val mn = ((totalMs % 3600_000) / 60_000).toInt()
                    val s = ((totalMs % 60_000) / 1000).toInt()
                    val ms = (totalMs % 1000).toInt()
                    tv.text = String.format(Locale.KOREA, "%02d:%02d:%02d.%03d", h, mn, s, ms)
                    ui.postDelayed(this, 100L)
                }
            }
        }
        ui.post(tick)
    }

    private fun updateOverlayText(text: String) {
        val v = overlayView ?: return
        v.findViewById<TextView>(R.id.tvCountdown).text = text
    }

    // ===================== 알림 =====================
    private fun buildNotification(targetMillis: Long): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "PrecisionTap", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PrecisionTap 대기 중")
            .setContentText("트리거 시각: ${sdf.format(Date(targetMillis))}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        cancelled = true
        triggerThread?.interrupt()
        overlayView?.let { wm?.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
