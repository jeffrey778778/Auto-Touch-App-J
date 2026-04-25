package com.zenops.precisiontap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * 다른 앱 위에서 dispatchGesture 로 실제 터치를 발생시키는 서비스.
 * Android 7.0 (API 24) 이상.
 *
 * 사용자 → 설정 → 접근성 → PrecisionTap 자동 터치 → 켜기.
 */
class AutoTapAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoTapAccessibilityService? = null
        val isRunning: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 사용 안 함 */ }
    override fun onInterrupt() { }

    /**
     * 지정 좌표를 단일 터치.
     * duration: 50ms 정도가 일반적. 너무 짧으면 인식 안 되는 앱 있음.
     */
    fun performTap(x: Float, y: Float, duration: Long = 50L) {
        // dispatchGesture 는 메인 스레드에서 호출해야 안전
        Handler(Looper.getMainLooper()).post {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }
}
