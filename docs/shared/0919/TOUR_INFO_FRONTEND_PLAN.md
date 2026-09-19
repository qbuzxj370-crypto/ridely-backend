# 관광 정보 프론트 작업 계획 (2026-09-19)

브랜치: `feature/tour-info-frontend` (main `ffc8c71` 기준)
근거 명세: `docs/shared/0918/TOUR_COURSE_INFO_SPEC.md` (백엔드는 끝났고 프론트만 남음), `docs/shared/FRONTEND_GUIDE.md` 5장 「관광지」

## 목표

AI가 코스에 넣은 관광지와 「왜 골랐는지(`reason`)」가 앱 화면에 전혀 안 보이는 문제를 해결한다. 데이터는 이미 `state.lastRecommend.waypoints`에 들어 있다.

## 이번 범위 (명세 그대로)

1. 추천 결과 화면(`route-result`)에 **경유지 목록** — `reason` 필수 표시, 관광지는 배지로 구분, 항목을 누르면 지도가 그 마커로 이동
2. 관광지 **상세 카드** — `GET /api/v1/tours/{id}` (썸네일·제목·주소·전화·개요)
3. 라이딩 중 관광지 **200m 근접 안내** — 단말에서 판정, 사고다발지 경고와 **별도 슬롯**

**하지 않는 것** (백엔드 선행 필요): 홈 화면 관광지 레이어, 코스 주변 관광지 전체 표시.

## 현재 코드 조사 결과 — 명세와 다른 점 / 미리 알아둘 것

| # | 발견 | 계획에 미치는 영향 |
|---|---|---|
| 1 | 명세는 「`map.js`의 기존 함수 재사용」이라 했지만 **지도 이동 함수가 없다.** `map.js`는 마커·폴리라인·폴리곤·`fitBounds`뿐이고, `riding.js`가 `map.setCenter`를 직접 쓴다 | `map.js`에 `panTo(map, lat, lng)` 헬퍼를 **새로 추가** |
| 2 | `route-result.js`의 `initMap()`이 지도 객체와 마커를 **밖으로 안 돌려준다**(`addMarker` 반환값도 버림) | 목록 클릭 → 마커 이동을 하려면 `initMap`이 `map`과 마커 배열을 반환/보관하도록 구조 변경 |
| 3 | `WaypointDTO`에는 `facilityType`이 없다. `ROUTE_FACILITY`가 급수대·화장실·인증센터를 한데 묶어 내려온다 | 배지 라벨은 `ROUTE_FACILITY` → 「편의시설」로 통칭 (세부 구분 불가) |
| 4 | `GET /routes/{id}`도 같은 `RouteRecommendResponseDTO`를 돌려주고 `waypoints_json`(type·id·reason 포함)이 그대로 저장돼 있다 | 저장한 코스로 라이딩을 시작해도 근접 안내가 **추가 API 없이** 동작 |
| 5 | `riding.js`의 `saveProgress()`는 `alertedZones`(사고다발지 인덱스)만 저장한다 | 앱이 죽었다 재개될 때 이미 알린 관광지를 또 알리지 않으려면 **`alertedTours`도 저장·복구**해야 함 |
| 6 | `alertReceivedCount`는 `alertedZones.size` | 관광 안내는 **이 카운트에 넣지 않는다** (안전 경고 통계 오염 방지). 별도 `Set` 사용 |
| 7 | main의 `api.js` `API_BASE`가 CloudFront 실배포 주소로 바뀌었다 | 로컬 백엔드로 테스트하려면 임시로 `localhost:8080`으로 되돌려야 함 (커밋 금지) |
| 8 | `waypoints[].reason`은 `aiProvider=FALLBACK`이면 항상 비어 있고, 아닐 때도 비어 있을 수 있다 | `reason`이 없으면 그 줄만 숨기고 이름·배지·거리는 그대로 표시 |

## 설계 결정 (검토 요청)

**A. 상세 카드 UI — 목록 항목 아래로 펼치는 인라인 카드(추천)**
관광지 항목을 누르면 그 항목 바로 아래에 카드가 펼쳐진다. 모달/바텀시트는 새 컴포넌트가 필요하고 이 앱엔 아직 없다. 다시 누르면 접힌다. 동시에 지도가 그 마커로 이동.

**B. 관광 안내 슬롯 — `#riding-tour-alert`를 새로 추가, 스타일은 경고와 구분**
사고다발지 경고(`#riding-danger-alert`, 빨간 `error-banner`)와 **시각적으로 달라야** 한다. 같은 빨간색이면 관광 안내가 안전 경고처럼 보인다. `components.css`에 `.info-banner`(중립/브랜드색) 1개를 추가한다.

**C. 안내 문구** — `📍 관광지 근처예요 (150m) — 선유도공원` 한 줄. `reason`은 붙이지 않는다(주행 중 긴 문장은 방해).

**D. 안전 원칙** — 근접 판정은 `haversineM`으로 단말에서만 한다. 서버로 나가는 건 `GET /tours/{관광지번호}`뿐이고 이는 위치정보가 아니다(`LOCATION_PRIVACY_ARCHITECTURE.md` 준수).

**E. 렌더링은 `textContent`/DOM API로** — 이름·`reason`·`overview`는 AI/외부 API 문자열이라 `innerHTML`에 넣지 않는다. 이미지는 `<img>` 요소를 만들어 `onerror`에서 숨긴다.

## 작업 단위 (단위별 add & commit, push는 마지막에)

1. **기반** — `index.html` CSP `img-src`에 `https://tong.visitkorea.or.kr` 추가, `map.js`에 `panTo` 추가, `components.css`에 `.info-banner`·경유지 목록용 스타일
2. **경유지 목록** — `route-result.html`/`route-result.js`: `initMap` 구조 변경(지도·마커 보관), 목록 렌더링(유형 배지, 이름, 출발점 기준 거리, `reason`), 클릭 시 `panTo`
3. **관광지 상세 카드** — `TOUR_ATTRACTION` 항목 클릭 시 `GET /tours/{id}` → 카드 펼침 (썸네일·제목·주소·전화·개요, 없는 필드는 필드 유무로 판단, `COMMON-004`/네트워크 오류 처리, 같은 항목 재클릭 시 접기, 응답 캐시)
4. **라이딩 근접 안내** — `riding.html`에 슬롯 추가, `riding.js`에 `alertedTours`·`checkTourProximity()`, `saveProgress`/`resumeInterruptedSession`에 저장·복구, 라이딩 시작 시 초기화
5. **문서** — `FRONTEND_ISSUES.md`에 항목 추가, 이 문서 체크리스트·테스트 결과 갱신

작업 순서는 요청하신 대로 **수정 → 테스트 → 버그수정 → 로직검토 → 최종수정**, 문서는 단위마다 갱신한다.

## 테스트 계획

- **정적 서버 + Chrome**: `fetch`를 스텁해 `waypoints`(관광지·따릉이·수리소·편의시설 섞인 응답, `reason` 있음/없음, FALLBACK)와 `/tours/{id}`(모든 필드 있음 / 사진·전화 없음 / 404) 응답을 주입해서 목록·펼침·접힘·마커 이동·이미지 실패 숨김 확인. 모바일 뷰포트로 레이아웃 확인
- **라이딩 근접 안내**: Chrome의 위치 오버라이드(가짜 좌표)로 관광지 200m 안팎을 오가게 해서 안내가 **한 번만** 뜨는지, 사고다발지 경고와 서로 안 덮이는지, `alertReceivedCount`에 안 섞이는지 확인
- **실기기(Cordova APK)**: 로컬 백엔드 + 실제 추천 응답으로 여의도한강공원 출발 코스에서 관광지가 목록에 나오는지, 이미지가 실제로 뜨는지 확인 (이미지 도메인의 https 접근 가능 여부는 여기서만 확실히 검증됨)

## 리스크 / 열린 질문

- **관광지가 안 섞이는 코스가 있다.** 명세대로 한강 축에서 출발해야 후보에 들어온다. 안 섞이면 목록엔 인프라 경유지만 나오고 배지도 안 뜬다(정상 동작)
- **이미지 실측 미확인**: 서버가 https로 바꿔주지만 `tong.visitkorea.or.kr`이 https로 실제 서빙되는지는 실기기에서 확인 필요. 안 되면 카드는 이미지 없이 뜬다(`onerror` 처리로 깨진 아이콘은 안 남김)
- **웹 버전(CloudFront)도 같은 `index.html`을 쓴다** — CSP `img-src` 변경은 앱·웹 양쪽에 그대로 적용됨
- **자동화 테스트 없음**: 프론트에는 JS 테스트 인프라가 없어 위 수동/스텁 검증에 의존한다
