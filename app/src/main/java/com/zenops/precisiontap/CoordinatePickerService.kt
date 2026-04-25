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
import android.widget.TextView
import android.widget.Toast

/**
 * 화면을 탭해서 좌표를 잡는 서비스.
 *
 * 흐름:
 *  1. 시작 → 카운트다운 5초 (이 동안 사용자는 대상 앱으로 이동 가능, 터치 통과)
 *  2. 카운트다운 종료 → 풀스크린 캡처 모드 (첫 탭의 raw 좌표를 잡음)
 *  3. 좌표를 SharedPreferences 에 저장
 *  4. MainActivity 를 다시 띄움 → MainActivity 가 좌표 자동 입력
 */
class CoordinatePickerService : Service() {

    companion object {
        private const val CHANNEL_ID = "precision_tap_picker"
        private const val NOTI_ID = 2
        private const val COUNTDOWN_SECONDS = 5
        const val PREFS_NAME = "PrecisionTapPrefs"
        const val KEY_PICKED_X = "picked_x"
        const val KEY_PICKED_Y = "picked_y"
        const val KEY_PICKED_AT = "picked_at"
    }

    private var wm: WindowManager? = null
    private var view: View? = null
    private var tvHint: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTI_ID, buildNotification())
        showOverlay()
        startCountdown()
        return START_NOT_STICKY
    }

    // 풀스크린 오버레이 추가. 처음에는 터치 통과 (FLAG_NOT_TOUCHABLE) 상태.
    private fun showOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val v = LayoutInflater.from(this).inflate(R.layout.picker_overlay, null)
        view = v
        tvHint = v.findViewById(R.id.tvHint)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // 카운트다운 동안 터치 통과 + 포커스 안 가져감
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        wm?.addView(v, params)
    }

    // 카운트다운 → 0초 도달 시 캡처 모드로 전환
    private fun startCountdown() {
        var remaining = COUNTDOWN_SECONDS
        val tick = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    tvHint?.text = "📍 좌표 잡기 ${remaining}초 후 시작\n대상 앱으로 이동하세요"
                    remaining--
                    ui.postDelayed(this, 1000L)
                } else {
                    enableCaptureMode()
                }
            }
        }
        ui.post(tick)
    }

    // 캡처 모드: 풀스크린 터치 가로채기 활성화
    private fun enableCaptureMode() {
        val v = view ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // 터치 통과 플래그 해제
        val newParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm?.updateViewLayout(v, newParams)
        } catch (_: Exception) { /* ignore */ }

        tvHint?.text = "👆 원하는 위치를 탭하세요"

        v.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX
                val y = event.rawY
                onCoordCaptured(x, y)
                true
            } else {
                false
            }
        }
    }

    private fun onCoordCaptured(x: Float, y: Float) {
        // 1. SharedPreferences 에 저장
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putFloat(KEY_PICKED_X, x)
            putFloat(KEY_PICKED_Y, y)
            putLong(KEY_PICKED_AT, System.currentTimeMillis())
            apply()
        }

        Toast.makeText(this, "좌표 저장: (${x.toInt()}, ${y.toInt()})", Toast.LENGTH_SHORT).show()

        // 2. MainActivity 재실행 (좌표 자동 입력은 MainActivity.onResume 에서)
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(launchIntent)

        // 3. 서비스 종료
        stopSelf()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Coordinate Picker",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("좌표 잡기 모드")
            .setContentText("화면을 탭하면 좌표가 저장됩니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        view?.let { wm?.removeView(it) }
        view = null
        super.onDestroy()
    }
}
