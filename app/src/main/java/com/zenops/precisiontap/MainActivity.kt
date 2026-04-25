package com.zenops.precisiontap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etHour: EditText
    private lateinit var etMinute: EditText
    private lateinit var etSecond: EditText
    private lateinit var etMillisecond: EditText
    private lateinit var etX: EditText
    private lateinit var etY: EditText
    private lateinit var etRepeat: EditText
    private lateinit var etInterval: EditText
    private lateinit var btnA11y: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnPickCoord: Button
    private lateinit var btnStart: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHour = findViewById(R.id.etHour)
        etMinute = findViewById(R.id.etMinute)
        etSecond = findViewById(R.id.etSecond)
        etMillisecond = findViewById(R.id.etMillisecond)
        etX = findViewById(R.id.etX)
        etY = findViewById(R.id.etY)
        etRepeat = findViewById(R.id.etRepeat)
        etInterval = findViewById(R.id.etInterval)
        btnA11y = findViewById(R.id.btnA11y)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnPickCoord = findViewById(R.id.btnPickCoord)
        btnStart = findViewById(R.id.btnStart)
        tvStatus = findViewById(R.id.tvStatus)

        // 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        btnA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "오버레이 권한 OK", Toast.LENGTH_SHORT).show()
            }
        }

        btnPickCoord.setOnClickListener { startCoordPicker() }
        btnStart.setOnClickListener { startScheduledTap() }
    }

    override fun onResume() {
        super.onResume()
        // CoordinatePickerService 가 저장한 좌표가 있으면 자동 입력
        val prefs = getSharedPreferences(CoordinatePickerService.PREFS_NAME, MODE_PRIVATE)
        val pickedAt = prefs.getLong(CoordinatePickerService.KEY_PICKED_AT, 0L)
        // 30초 이내에 잡힌 좌표만 사용 (오래된 값 무시)
        if (pickedAt > 0 && System.currentTimeMillis() - pickedAt < 30_000L) {
            val x = prefs.getFloat(CoordinatePickerService.KEY_PICKED_X, -1f)
            val y = prefs.getFloat(CoordinatePickerService.KEY_PICKED_Y, -1f)
            if (x >= 0 && y >= 0) {
                etX.setText(x.toInt().toString())
                etY.setText(y.toInt().toString())
                tvStatus.text = "좌표 입력됨: (${x.toInt()}, ${y.toInt()})"
            }
            // 한 번 사용했으면 클리어
            prefs.edit().clear().apply()
        }
    }

    private fun startCoordPicker() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한 먼저 허용", Toast.LENGTH_SHORT).show(); return
        }
        Toast.makeText(this,
            "5초 후 좌표 잡기 시작. 그 동안 대상 앱으로 이동하세요.",
            Toast.LENGTH_LONG).show()
        startForegroundService(Intent(this, CoordinatePickerService::class.java))
        // 백그라운드로 보내서 사용자가 다른 앱으로 이동 가능하게
        moveTaskToBack(true)
    }

    private fun startScheduledTap() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한 먼저 허용", Toast.LENGTH_SHORT).show(); return
        }
        if (!AutoTapAccessibilityService.isRunning) {
            Toast.makeText(this, "접근성 서비스를 켜주세요", Toast.LENGTH_SHORT).show(); return
        }

        val h = etHour.text.toString().toIntOrNull()
        val m = etMinute.text.toString().toIntOrNull()
        val s = etSecond.text.toString().toIntOrNull()
        val ms = etMillisecond.text.toString().toIntOrNull() ?: 0
        val x = etX.text.toString().toFloatOrNull()
        val y = etY.text.toString().toFloatOrNull()

        if (h == null || m == null || s == null || x == null || y == null) {
            Toast.makeText(this, "시각/좌표를 모두 입력", Toast.LENGTH_SHORT).show(); return
        }

        val repeat = (etRepeat.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
        val interval = (etInterval.text.toString().toLongOrNull() ?: 0L).coerceAtLeast(0L)

        val now = System.currentTimeMillis()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, s)
            set(Calendar.MILLISECOND, ms)
        }
        if (target.timeInMillis <= now) target.add(Calendar.DAY_OF_MONTH, 1)
        val targetMillis = target.timeInMillis

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TARGET_MILLIS, targetMillis)
            putExtra(OverlayService.EXTRA_X, x)
            putExtra(OverlayService.EXTRA_Y, y)
            putExtra(OverlayService.EXTRA_REPEAT, repeat)
            putExtra(OverlayService.EXTRA_INTERVAL, interval)
        }
        startForegroundService(intent)

        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA)
        tvStatus.text = "예약: ${sdf.format(target.time)} @ ($x, $y)  ${repeat}회 / ${interval}ms"
        Toast.makeText(this, "타이머 시작. 대상 앱으로 이동하세요.", Toast.LENGTH_LONG).show()
    }
}
