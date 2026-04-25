# GitHub Actions로 APK 자동 빌드 받기 (초보자용)

PC/노트북에 아무것도 설치할 필요 없음. 브라우저만 있으면 됨.
완료하면 **PrecisionTap.apk 파일**이 손에 들어와요. 그걸 폰으로 옮겨서 설치하면 끝.

---

## 사전 준비

- GitHub 계정 (없으면 https://github.com → "Sign up" → 무료 가입, 이메일 인증)
- 다운로드한 **PrecisionTap.zip** 파일 압축 해제 (PrecisionTap 폴더가 나와야 함)

---

## 절차 (10분 소요)

### 1. 새 저장소(repository) 만들기
1. GitHub 로그인 후 우측 상단 **`+` → `New repository`** 클릭
2. **Repository name**: `PrecisionTap` 입력
3. **Public** 선택 (무료 사용자도 Actions 무료 한도 충분함)
4. 다른 옵션은 기본값 유지, **`Create repository`** 클릭

### 2. 파일 업로드
1. 새로 생긴 빈 저장소 화면에서 **`uploading an existing file`** 링크 클릭
   (또는 `Add file` → `Upload files`)
2. **PrecisionTap 폴더 안의 모든 파일/폴더를 드래그**해서 업로드 박스에 떨어뜨림
   - ⚠️ 폴더 자체가 아니라 **폴더 안의 내용물**을 올려야 함
   - `.github`, `app`, `gradle.properties`, `build.gradle.kts`, `settings.gradle.kts` 등 모두 포함
   - 숨김 폴더(`.github`, `.gitignore`)도 반드시 포함되어야 함
3. 화면 하단 **`Commit changes`** 클릭

### 3. 빌드 시작 확인
1. 저장소 상단 메뉴에서 **`Actions`** 탭 클릭
2. "Build APK" 라는 워크플로우가 자동으로 실행 중인 게 보임 (노란 점 = 진행 중)
3. 5~10분 대기 → 초록 체크(✓) 뜨면 완료

### 4. APK 다운로드
1. 완료된 워크플로우 항목 클릭해서 들어감
2. 하단 **Artifacts** 섹션에 `PrecisionTap-debug-apk` 라는 zip이 있음
3. 클릭해서 다운로드 → 압축 해제 → **`app-debug.apk`** 가 들어있음
4. 이 파일을 폰으로 옮김 (USB / 카톡 / 클라우드 어떤 방식이든)

### 5. 폰에 설치
1. 안드로이드 폰: **설정 → 보안 → 출처를 알 수 없는 앱 설치 허용**
   (폰마다 메뉴 위치 조금씩 다름. "알 수 없는 앱" 으로 검색)
2. 폰에서 `app-debug.apk` 파일 탭 → 설치

---

## 코드 수정 후 다시 빌드하려면

GitHub 웹페이지에서 파일을 직접 수정하거나, 같은 저장소에 새 파일을 업로드하면 **자동으로 다시 빌드되고 새 APK가 나옴**. Actions 탭에서 매번 새 결과물 다운로드.

---

## 빌드 실패 시

Actions 탭 → 실패한 워크플로우 클릭 → 빨간 X 단계에서 로그 확인.
가장 흔한 원인:
- 파일을 폴더 안에 또 폴더로 올린 경우: `app/` 폴더가 저장소 루트에 바로 있어야 함. `PrecisionTap/app/...` 처럼 한 번 더 들어가있으면 안 됨.
- `.github/workflows/build.yml` 이 누락된 경우: 숨김 폴더라 업로드 시 빠지기 쉬움. 압축 해제 후 폴더 옵션에서 "숨김 파일 보기" 켜고 다시 확인.

---

## 자동 빌드 비용
GitHub Actions Public 저장소는 **무료 무제한**. Private으로 바꿀 거면 월 2000분 무료까지 가능.
