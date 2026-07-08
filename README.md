# Ridely (라이들리)

> **흩어진 자전거 인프라를 활용해 당신의 라이딩을 키우는 AI 트레이너**

한국관광공사 관광 데이터(TourAPI)와 자전거 인프라 공공데이터(음수대·수리소·따릉이·사고다발지)를 AI로 통합하고, AI 코치 **"Coach Ridely"** 가 라이더의 목적(편의·운동·풍경)에 맞춰 경로를 설계·코칭하는 모바일 앱 서비스.

**2026 관광데이터 활용 공모전** (웹·앱 개발 부문, 한국관광공사) 출품작.

<!-- TODO(본선 이후): 시연 스크린샷 / 데모 GIF / 발표 자료 링크 -->

---

## 주요 기능 (MVP)

| 기능                   | 설명                                                          |
|----------------------|-------------------------------------------------------------|
| **AI 코스 설계**         | 출발지·목표 거리·우선순위(편의/운동/풍경) 입력 → 인프라가 자연스럽게 경유되는 코스 + 트레이너 코멘트 |
| **경로 시각화**           | 지도 위 경로 + 인프라 5종 마커 + 사고다발지 3등급 히트맵 (레이어 토글)                |
| **코스 상세 + 다음 단계 제안** | 거리·시간·누적고도·강도 라벨 + "다음엔 +2km 어때요?" 코칭                       |
| **경로 저장 & 라이딩 통계**   | 저장 경로 관리 + 누적 거리·횟수·평균 강도 (RAG 기반 개인화)                      |

---

## 기술 스택

| 영역             | 스택                                                                    |
|----------------|-----------------------------------------------------------------------|
| **언어 · 프레임워크** | Java 25 LTS · Spring Boot 3.5.14 (Maven)                              |
| **AI**         | Spring AI 1.1.6 · Gemini (Google Gen AI Starter) · text-embedding-004 |
| **DB**         | PostgreSQL 16 + PostGIS (공간) + pgvector (RAG)                         |
| **DB 접근**      | MyBatis 3.0.4 (단순 CRUD) + JdbcClient (PostGIS·pgvector)               |
| **프론트엔드**      | 순수 JavaScript + Kakao Map JS SDK                                      |
| **모바일**        | Apache Cordova 13 (Android, Phase 3부터)                                |
| **외부 API**     | OpenRouteService · Kakao Local · TAAS · 서울 열린데이터광장                    |

---

## 프로젝트 구조

```
ridely/
├── docs/                        # 기획서·ADR·API 명세·스프린트
├── src/main/java/kr/ridely/
│   ├── controller/              # Web 레이어
│   ├── service/                 # 비즈니스 로직 (Interface + Impl)
│   │   └── route/pipeline/      # AI 경로 추천 5단계 파이프라인
│   ├── dao/                     # MyBatis Mapper 인터페이스
│   ├── dto/{도메인}/             # 도메인별 DTO
│   ├── vo/                      # 엔티티
│   ├── common/                  # ApiResponse·ErrorCode·유틸
│   ├── config/                  # Security·CORS·MyBatis 설정
│   └── infra/                   # 외부 연동 (llm·embedding·ors·kakao·publicdata)
├── src/main/resources/
│   ├── mapper/                  # MyBatis XML (dao와 1:1)
│   ├── db/{ddl,seed}/           # 스키마·시드 SQL
│   └── static/                  # 프론트 정적 파일
└── android-shell/               # Cordova (Phase 3에서 생성)
```

도메인 6개: `auth` `user` `route` `poi` `savedroute` `ridehistory`

---

## 시작하기

### 사전 요구사항

| 항목             | 버전                                                        |
|----------------|-----------------------------------------------------------|
| JDK            | 25                                                        |
| Docker         | 최신 (PostgreSQL + PostGIS + pgvector 컨테이너)                 |
| GOOGLE_API_KEY | [AI Studio](https://aistudio.google.com/apikey)에서 발급 (무료) |

### 실행

```bash
# 1. 환경변수 설정 (.env.example 참고)
cp .env.example .env   # 후 키 채우기

# 2. DB 기동
docker compose up -d

# 3. local 프로파일로 실행
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. 확인
curl http://localhost:8080/api/v1/health
```

---

## 대회 정보

- **대회**: 2026 관광데이터 활용 공모전 (한국관광공사)
- **부문**: ① 웹·앱 개발 부문
- **핵심 요건**: 한국관광공사 OpenAPI(TourAPI) 활용 필수
- **MVP 범위**: 한강 자전거길 서울 구간 (아라한강갑문~잠실, 약 40km)

<!-- TODO(본선 이후): 수상 내역, 발표 자료, 시연 영상 링크 -->