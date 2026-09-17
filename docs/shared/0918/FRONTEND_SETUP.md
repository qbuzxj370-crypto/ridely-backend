# 프론트엔드(Cordova) 개발 환경 세팅

Ridely 앱은 `android-shell/` 안에 있는 Apache Cordova 프로젝트다. 순수 HTML/CSS/JS로 만들어져 있고(`www/`), 백엔드는 이 저장소의 Spring Boot 서버를 그대로 호출한다.

## 1. 설치할 것

| 항목 | 용도 |
|---|---|
| Node.js + npm | Cordova CLI 실행 |
| Cordova CLI (`npm install -g cordova`) | 프로젝트 빌드/실행 |
| Android Studio (SDK 포함) | 안드로이드 빌드·에뮬레이터·기기 관리 |
| **JDK 25** | 백엔드(Spring Boot) 빌드·실행용 |
| **JDK 17** | **안드로이드 앱 빌드 전용** — 아래 참고 |

### ⚠️ JDK가 두 개 필요한 이유

백엔드는 `pom.xml`이 Java 25를 요구하지만, Cordova의 안드로이드 빌드(Gradle/Android Gradle Plugin)는 **JDK 25를 지원하지 않는다.** 그래서:

- 시스템 기본 `JAVA_HOME`은 **JDK 25**로 둔다 (백엔드용, IDE 등 대부분의 작업이 이걸 씀)
- **안드로이드 빌드 명령을 실행하는 그 터미널 세션에서만** `JAVA_HOME`을 JDK 17로 임시로 바꾼다:

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cordova run android
```

(경로는 실제 설치 위치에 맞게. PowerShell이면 `$env:JAVA_HOME = "..."`, `$env:Path = "$env:JAVA_HOME\bin;" + $env:Path`)

시스템 전체 `JAVA_HOME`을 17로 바꾸면 안 된다 — 그러면 백엔드가 안 켜진다.

## 2. 프로젝트 실행

```cmd
:: 브라우저에서 빠르게 확인 (개발 중 대부분은 이걸로 충분)
cordova platform add browser   :: 최초 1회
cordova run browser

:: 실제 안드로이드 기기/에뮬레이터
:: (위의 JDK 17 설정을 먼저 하고)
cordova run android
```

## 3. 실기기 테스트 — 로컬 백엔드에 붙이기

로컬에서 `mvnw spring-boot:run`으로 띄운 백엔드에 실기기 앱이 접속하려면 **USB 디버깅 + adb reverse**가 필요하다. 프론트 코드(`www/js/api.js`)의 `API_BASE`가 `http://localhost:8080`으로 고정돼 있고, 폰의 `localhost`를 PC의 `localhost`로 연결해주는 게 이 명령이다:

```cmd
adb reverse tcp:8080 tcp:8080
```

**⚠️ USB 케이블을 뽑았다 다시 꽂을 때마다 이 명령을 다시 실행해야 한다.** 연결이 끊기면 이 터널 설정 자체가 초기화된다. 앱에서 갑자기 모든 API 호출이 `Failed to fetch`로 실패하면 십중팔구 이거다 — 백엔드 코드 문제가 아니라 터널이 끊긴 것이니 먼저 이것부터 확인할 것.

에뮬레이터를 쓰면 이 문제가 없다 — 에뮬레이터는 `http://10.0.2.2:8080`으로 호스트 PC를 바로 부를 수 있다 (다만 지금 `API_BASE`는 `localhost`로 고정돼 있어 에뮬레이터에서 쓰려면 이 값을 바꿔야 한다).

## 4. 프로젝트 구조

```
android-shell/
  config.xml              # 앱 id(kr.ridely.app)·이름, 아이콘/스플래시, 권한, CSP 관련 네이티브 설정
  build.json              # 릴리즈 서명 정보 (gitignore 대상 — RELEASE.md 참고)
  release-key.jks         # 릴리즈 서명 키 (gitignore 대상)
  www/
    index.html             # 유일한 진입점 — 헤더 + 하단 탭바 + <main id="app">
    css/
      tokens.css            # 색상 팔레트 (CSS 변수)
      base.css / components.css
    js/
      app.js                # deviceready 이후 라우터 초기화
      router.js             # 해시 라우팅(#/route-plan 등), 화면 전환
      api.js                # fetch 래퍼 — API_BASE, 인증 헤더, 401 처리, Idempotency-Key
      auth.js               # 로그인/회원가입/로그아웃, 토큰 저장
      token-store.js         # localStorage 토큰 읽기/쓰기
      state.js              # 화면 간 공유 상태 (선택한 코스 등)
      map.js                # 카카오맵 SDK 로드·마커·폴리라인 헬퍼
      pages/*.js            # 화면별 로직 — render(container) 내보냄
    pages/*.html            # 화면별 마크업 조각. 라우터가 fetch로 불러와 #app에 넣음
```

화면 8개: 홈, 로그인/회원가입, 코스 추천 입력, 코스 결과, 저장 경로, 라이딩, 라이딩 기록, 마이페이지.

## 5. 카카오맵 관련 — 오리진이 `file://`가 아니다

**cordova-android 12+ (지금 쓰는 버전 15.1.0)는 앱을 `file://`가 아니라 가상 오리진 `https://localhost`(포트 없음)로 서빙한다** (`androidx.webkit.WebViewAssetLoader` 사용). 오래된 Cordova 지식으로 `file://`를 가정하면 안 된다 — 실측(2026-09-17)으로 확인된 사실이다.

이게 영향을 주는 곳:
- **카카오 디벨로퍼스 콘솔**: JS SDK를 쓰려면 앱의 "JavaScript SDK 도메인"에 `https://localhost`를 등록해야 한다 (플랫폼 키 → JavaScript 키 항목 안에 있음, "제품 링크 관리"가 아님).
- **백엔드 CORS**: 백엔드 쪽에도 이 오리진이 허용돼 있어야 한다. 상세는 `docs/shared`의 백엔드 변경 관련 문서나 백엔드 담당에게 문의.

## 6. CSP (Content Security Policy)

`www/index.html`의 `<meta http-equiv="Content-Security-Policy">`가 이 앱이 불러올 수 있는 리소스 출처를 제한한다. 새 외부 API/도메인을 붙이면 (예: 새 지도 서비스, 새 CDN) 여기 도메인을 추가해야 로드가 안 막힌다. 현재 허용된 것: 카카오맵(`dapi.kakao.com`, `*.daumcdn.net`), 로컬 개발 백엔드(`http://localhost:8080`, `http://10.0.2.2:8080`).

배포 주소로 API_BASE를 바꿀 때 이 CSP의 `connect-src`에도 그 주소를 추가해야 한다.

## 7. 알려진 한계

- **백그라운드 GPS 추적 불가**: 화면이 꺼지거나 앱이 백그라운드로 가면 안드로이드가 프로세스를 죽일 수 있어 라이딩 추적이 끊긴다. 진행 상황을 로컬 저장(localStorage)해서 앱이 다시 켜지면 이어서 추적하지만, 완전히 막으려면 `cordova-plugin-background-mode` 같은 네이티브 플러그인이 필요하다(아직 미도입).
- **스플래시 화면이 매우 짧음**: 안드로이드 12+ 네이티브 스플래시 API는 노출 시간을 강제로 짧게 제한한다(~1초 이하). 더 길게 보여주려면 `MainActivity.java`에 코드 추가가 필요하다(아직 미도입).
