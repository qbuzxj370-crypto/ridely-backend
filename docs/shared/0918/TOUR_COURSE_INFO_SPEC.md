# 추천 코스 관광 정보 표시 — 프론트 작업 명세 (2026-09-18)

백엔드는 끝났다. 이 문서는 `android-shell/www/` 쪽에서 할 일을 적는다.

## 배경

`tour_attraction`에 관광지·문화시설·음식점 710건이 적재돼 있는데 **앱 어디에도 관광지가 보이지 않는다.** `android-shell/www/`에서 `tours` 문자열이 한 번도 나오지 않는다.

AI는 코스를 설계할 때 이미 관광지를 경유지로 고르고 있고, 왜 골랐는지까지 문장으로 만들어 응답에 담는다(`waypoints[].reason`). 화면이 그걸 표시하지 않아 사용자에게 닿지 않을 뿐이다.

## 이번 범위

**추천 코스 맥락에서만** 관광 정보를 보여준다. 홈 화면의 관광지 레이어와 자유 주행 중 안내는 이번에 하지 않는다 — 이유는 마지막 절에 있다.

---

## 1. 추천 결과 화면에 경유지 목록

`www/pages/route-result.html`에 경유지 영역이 **없다.** 지금은 지도에 이름만 달린 마커로 찍히고 끝난다. 목록을 새로 만든다.

데이터는 `state.lastRecommend.waypoints`에 이미 들어 있다. **추가 API 호출이 필요 없다.**

```json
{
  "type": "TOUR_ATTRACTION",
  "id": 12,
  "name": "선유도공원",
  "lat": 37.5434, "lng": 126.8997,
  "distanceFromStartKm": 8.4,
  "reason": "8km 지점, 잠깐 쉬면서 한강 풍경 챙기기 좋아요"
}
```

- `type`은 `TOUR_ATTRACTION`(관광지) · `BIKE_STATION`(따릉이) · `REPAIR_SHOP`(수리센터) · `ROUTE_FACILITY`(급수대·화장실·인증센터) · `BIKE_PARKING`(보관소)
- **`reason`을 반드시 보여준다.** 「여기를 지나갑니다」가 아니라 「왜 이곳인지」를 AI가 쓴 문장이고, 다른 지도 앱과 갈리는 지점이 여기다
- `TOUR_ATTRACTION`은 배지로 구분한다. 인프라 경유지와 섞이면 관광지가 코스에 들어가 있다는 것이 안 드러난다
- 항목을 누르면 지도가 그 마커로 이동한다 (`map.js`의 기존 함수 재사용)

## 2. 관광지 상세 카드

`TOUR_ATTRACTION` 경유지를 누르면 상세를 띄운다.

```
GET /api/v1/tours/{id}     ← 경유지의 id를 그대로 넣는다. 토큰 불필요
```

표시할 것: 썸네일(`thumbnailUrl`) · 제목(`title`) · 주소(`addr1` + `addr2`) · 전화(`tel`) · 개요(`overview`)

⚠️ **값이 없는 콘텐츠가 많고, 없으면 필드 자체가 응답에서 빠진다.** 전역 설정이 `non_null`이라 `=== null` 비교는 실패한다. 필드 유무로 판단할 것. 상세는 `FRONTEND_GUIDE.md` 5장 「관광지」 절에 있다.

⚠️ **CSP를 고치지 않으면 이미지가 안 뜬다.** `www/index.html`의 `img-src`에 관광 이미지 도메인이 없다.

```
img-src 'self' data: blob: https://tong.visitkorea.or.kr https://*.daumcdn.net https://*.kakao.com;
```

이미지 주소는 **서버가 `https`로 바꿔서 내보낸다.** 원본이 `http://tong.visitkorea.or.kr/...`인데 앱이 https에서 뜨므로(Cordova는 `https://localhost`, 웹은 CloudFront) 그대로면 혼합 콘텐츠로 막힌다. 받은 값을 프론트에서 손댈 필요는 없다.

그래도 `onerror`로 이미지 요소를 감추는 처리는 넣어 두자. 일부 이미지가 실패해도 카드가 깨진 아이콘으로 남지 않게.

## 3. 라이딩 중 관광지 근접 안내

`www/js/pages/riding.js`. 사고다발지 근접 경고와 **같은 구조**다.

- 대상은 `state.lastRecommend.waypoints` 중 `type === 'TOUR_ATTRACTION'`
- 거리 기준 200m. 사고다발지 경고의 `DANGER_ALERT_DISTANCE_M`와 같은 값이다
- 한 번 알린 관광지는 다시 알리지 않는다. `alertedZones`와 같은 `Set` 방식
- 판정은 `haversineM`으로 단말에서 한다. **서버를 부르지 않는다**

⚠️ **경고 슬롯을 따로 만든다.** `#riding-danger-alert`를 같이 쓰면 안 된다. 한 슬롯에 쓰면 뒤에 오는 메시지가 앞의 것을 덮는데, 관광 안내가 안전 경고를 지우는 순서가 나온다. 슬롯을 나눈 이유가 그것이라 같은 실수를 반복하게 된다.

## 제약

**위치정보는 서버로 보내지 않는다.** 근접 판정은 전부 단말에서 한다. `/tours/{id}`는 관광지 번호로 부르는 것이라 위치정보가 아니다. 이 원칙은 `LOCATION_PRIVACY_ARCHITECTURE.md`에 있다.

**상세 조회 응답에 `distanceM`이 없다.** 기준점 없이 한 건을 읽는 것이라 거리가 성립하지 않는다. 목록에 거리를 쓰려면 경유지의 `distanceFromStartKm`(출발점 기준)를 쓴다.

## 이번에 하지 않는 것

둘 다 백엔드 작업이 먼저 필요하다.

- **홈 화면의 관광지 레이어** — `/tours/nearby`가 반경 내 0건에 `POI-001`(404)을 낸다. 레이어 토글로 쓰면 0건이 정상 상태라 매번 에러 배너가 뜬다. `/pois/nearby`처럼 200에 빈 배열로 바꾸는 것이 선행이다
- **코스 주변 관광지 전체** — 경유지로 뽑히지 않은 주변 관광지까지 지도에 뿌리려면 `recommended_route.route_geom` 기준 조회가 필요한데 아직 없다. 기존 `TourSpatialDao.findAlongCorridor`는 출발·도착을 잇는 **직선** 축 기준이라 실제 코스 형상과 다르다

## 확인 방법

```
GET /api/v1/tours/1
GET /api/v1/tours/999999     → 404 COMMON-004
```

추천을 한 번 받아 응답의 `waypoints`에 `TOUR_ATTRACTION`이 섞여 나오는지 먼저 보면 된다. 한강 축에서 출발지를 잡아야 관광지가 후보에 들어온다 (예: 여의도한강공원 `37.5265, 126.9339`).
