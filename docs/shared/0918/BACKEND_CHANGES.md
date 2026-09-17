# 백엔드 변경 사항 — 프론트엔드(Cordova) 연동 대응 (2026-09-18)

프론트엔드를 실기기에 연결하는 과정에서 발견·수정한 백엔드 코드 변경 사항이다. 전부 `local` 프로파일 기준으로 고쳤고, 코드 변경분은 커밋하면 배포에도 자동 반영되지만 **설정값(CORS 허용 오리진) 하나는 배포 서버에 별도 반영이 필요하다 — 4장 참고.**

## 1. CORS 허용 오리진에 Cordova 앱의 실제 오리진이 없었다

`application.yml`의 `ridely.cors.allowed-origins`에 `http://localhost:8080`만 있었다. **cordova-android 12+ (지금 프론트가 쓰는 15.1.0)는 앱을 `file://`가 아니라 가상 오리진 `https://localhost`(포트 없음)로 서빙한다** — 실기기 로그로 실측 확인. 그래서 이 오리진을 추가했다:

```yaml
allowed-origins:
  - http://localhost:8080
  - http://10.0.2.2:8080      # 안드로이드 에뮬레이터
  - http://localhost:8100     # cordova serve
  - https://localhost         # 실기기 WebView (cordova-android 12+)
  - file://                   # 구버전 대비
  - "null"
```

## 2. CORS 프리플라이트(OPTIONS)가 보호 경로에서 401로 막힘

인증이 필요한 API(`/saved-routes`, `/riding-sessions/**` 등)를 호출할 때 브라우저가 먼저 보내는 OPTIONS 프리플라이트가 `Authorization` 헤더 없이 오는데, `SecurityConfig`의 `.anyRequest().authenticated()`에 걸려 401이 났다. OPTIONS는 인증 없이 통과하도록 규칙을 추가했다 (`SecurityConfig.java`).

## 3. (핵심 원인) 만료된 토큰 응답에 CORS 헤더 자체가 안 붙던 문제

가장 오래 걸린 버그. `JwtAuthenticationFilter`가 토큰이 만료/무효하면 `ApiErrorWriter`로 응답을 직접 쓰고 필터 체인을 끊는데, 기존 CORS 설정(`WebMvcConfigurer.addCorsMappings`)은 `DispatcherServlet` 단계에서만 동작해서 이 경우 CORS 헤더가 전혀 안 붙었다. 브라우저는 401 응답을 받고도 CORS 헤더가 없어서 JS가 못 읽고 `Failed to fetch`를 던졌다 — 겉보기엔 네트워크 문제 같지만 실제로는 "로그인 만료"였다.

**조치**: CORS를 Spring Security 필터 체인에 직접 통합했다. `CorsConfig`가 `CorsConfigurationSource` 빈을 노출하고, `SecurityConfig`가 `.cors(Customizer.withDefaults())`로 물린다 — Security의 CORS 필터가 체인 맨 앞에서 실행되므로 이후 어떤 필터가 응답을 일찍 끊어도 CORS 헤더가 이미 붙어 있다.

## 4. ⚠️ 배포(EC2) 반영 시 놓치기 쉬운 것

**`application-prod.yml`은 `ridely.cors.allowed-origins`를 `${CORS_ALLOWED_ORIGINS}` 환경변수로 완전히 덮어쓴다.** 즉 1장에서 로컬에 추가한 CORS 오리진은 **배포 서버에는 반영되지 않는다.**

Cordova 앱이 배포된 백엔드를 바라보게 API 주소를 바꿀 때(`android-shell/www/js/api.js`의 `API_BASE`), **EC2 인스턴스의 `CORS_ALLOWED_ORIGINS` 환경변수에도 `https://localhost`를 추가**해야 앱이 정상 호출된다. 2번(OPTIONS permitAll)·3번(CORS-Security 통합)은 코드 변경이라 배포하면 자동 반영된다.
