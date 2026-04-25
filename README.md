# PrecisionTap

특정 시각(밀리초 단위)에, 다른 앱 위에서 지정 좌표를 자동 터치하는 Android 앱.

## 핵심 동작
- **AccessibilityService** + `dispatchGesture()` 로 다른 앱 위에서도 실제 터치 발생 (루트 불필요).
- **Foreground Service** 로 백그라운드 종료 방지 + 플로팅 카운트다운.
- **하이브리드 정밀 타이머**: 목표 -8ms 까지 `Thread.sleep`, 마지막 8ms 는 `currentTimeMillis()` spin-wait. 일반 `Handler.postAtTime` 보다 더 안정적인 ms 정확도.

## 빌드
1. Android Studio (Hedgehog | 2023.1.1+) 에서 **Open Existing Project** → `PrecisionTap` 폴더 선택.
2. Gradle Sync 자동 실행. 첫 sync 시 Gradle Wrapper(8.x) 다운로드되면서 wrapper jar 가 생성됨.
3. `Build → Build Bundle(s)/APK(s) → Build APK(s)` → `app/build/outputs/apk/debug/app-debug.apk`.

> **Gradle Wrapper jar 가 없다면**:
> 터미널에서 프로젝트 루트에 들어가서
> ```
> gradle wrapper --gradle-version 8.7
> ```
> (시스템에 gradle 8.x 가 있어야 함). 또는 Android Studio가 import 시 자동 생성.

## 사용 절차
1. APK 설치 후 앱 실행.
2. **① 접근성 권한 켜기** → 설정에서 "PrecisionTap 자동 터치" 활성화.
3. **② 다른 앱 위 표시 권한** → 허용.
4. 트리거 시각(HH:MM:SS.mmm), 좌표(X, Y), 반복 횟수/간격 입력.
5. **③ 타이머 시작** → 대상 앱으로 이동. 좌상단 카운트다운 오버레이가 시각 도달 시 자동 터치.

## 좌표 확인 방법
- **개발자 옵션 → 포인터 위치 표시** ON: 화면 상단에 현재 터치 X/Y 표시됨.
- 또는 ADB: `adb shell getevent -l` 로 이벤트 캡처.
- 좌표는 **물리 픽셀 절대좌표**. 디바이스 회전/해상도 변경 시 다시 측정 필요.

## 정확도
- `currentTimeMillis()` 자체는 보통 ms 해상도이지만, **단말 시계가 정확해야 함** (NTP 동기화 권장).
- `dispatchGesture()` 는 큐잉 후 비동기 디스패치되어 추가 1~3ms 지연 가능. 정확히 T 시각에 터치를 원하면 입력 시 `T - 2ms` 입력으로 보정 권장.
- 진동수 높은 게임/이벤트 앱은 입력 인식까지 추가 프레임(16~33ms) 지연 있을 수 있음.

## 알려진 제한
- 일부 앱(은행, 보안 키패드, 보호된 화면)은 AccessibilityService의 gesture를 차단함 (시스템 보안 정책).
- 제조사별 배터리 최적화로 백그라운드 thread가 sleep 동안 deep sleep 진입 가능 → 화면을 켜둔 상태로 사용 권장. 필요 시 `WakeLock` 추가 (코드에 미포함, 요청 시 추가).
- API 24(Android 7.0) 미만은 `dispatchGesture` 미지원 → 동작 불가.

## 폴더 구조
```
PrecisionTap/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/zenops/precisiontap/
        │   ├── MainActivity.kt              # 입력 UI + 권한 진입
        │   ├── AutoTapAccessibilityService.kt   # 실제 터치
        │   └── OverlayService.kt            # FG 서비스 + 정밀타이머 + 오버레이
        └── res/
            ├── layout/activity_main.xml
            ├── layout/overlay_layout.xml
            ├── values/strings.xml
            └── xml/accessibility_service_config.xml
```

## 추가 가능 (요청 시)
- 화면을 직접 탭해서 좌표 캡처(오버레이 크로스헤어).
- 다중 좌표 시퀀스 (좌표1 → 좌표2 → ...).
- 서버 시간 동기화 (NTP) 호출 후 보정.
- WakeLock + 화면 ON 유지.
