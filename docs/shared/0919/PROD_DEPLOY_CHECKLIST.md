# 실서버(EC2 + CloudFront) 배포 시 고려할 것 (2026-09-19 작업분)

이번에 만든 것 — 관광 정보 프론트, `GET /pois/all`(전체 조회 API), 「내 주변 인프라」(폰에서 거르기), XSS 이스케이프 — 을 실서버에 올릴 때 확인·결정할 것을 모았다. **로컬로는 확인할 수 없어서 아직 안 본 것**과 **배포하면서 놓치기 쉬운 것**이 중심이다. 표시 규칙: ✅ 확인함(근거 있음) / ⬜ 배포 후 확인 필요 / ❓ 결정·확인 필요.

관련 문서: `NEARBY_INFRA_LOCAL_PLAN.md`(설계·실측), `TOUR_INFO_FRONTEND_PLAN.md`, `docs/shared/0918/BACKEND_CHANGES.md`(CORS), `docs/shared/FRONTEND_GUIDE.md` 5장(`/pois/all` 명세).

## 1. 이번 배포에 실리는 것

| 구분 | 내용 | 배포 영향 |
|---|---|---|
| 백엔드 | 새 API `GET /api/v1/pois/all`, 약한 ETag 필터(`HttpCacheConfig`), 서버 메모리 캐시 5분 | main 병합 시 `deploy.yml`이 자동 배포 |
| 백엔드 | **DB 마이그레이션 없음**(`db/migration` 변경 0건), **새 환경변수 없음**, 새 의존성 없음 | Flyway·EC2 `.env` 손 볼 것 없음 |
| 백엔드 | 기존 API(`/pois/nearby` 등)는 그대로 | 이미 설치된 옛 앱 빌드는 영향 없음 |
| 앱(Cordova) | 관광 정보, 내 주변 인프라, XSS 이스케이프 | **앱은 서버 배포로 안 바뀐다. 새 APK를 다시 빌드·배포해야 한다** |

✅ 새 엔드포인트는 `SecurityConfig`의 `/api/v1/pois/**`(공개) 안에 들어가므로 `PUBLIC_PATHS`를 따로 고칠 필요가 없다(`PoiAllTest`가 비로그인 200을 고정).

## 2. 배포 순서

1. **백엔드 먼저**(#39 → API PR 순), 배포 후 4장 확인을 통과한 다음
2. **앱 새 빌드**를 배포한다

이유: 새 앱이 `/pois/all`을 부르는데 서버에 아직 없으면 홈의 「내 주변 인프라」가 서버 오류 안내로 끝난다(저장본이 없는 첫 사용자). 반대로 백엔드만 먼저 올리는 것은 옛 앱에 아무 영향이 없다.

`deploy.yml`은 테스트 없이 jar를 빌드해 올리고 30초 안에 `/api/v1/health`가 안 뜨면 직전 jar로 롤백한다. 이번 변경은 마이그레이션이 없어 기동 시간이 늘 이유가 없다.

## 3. 배포 전 확인

### 3-1. 실서버 DB에 적재된 데이터 ⬜
`/pois/all`은 **DB에 적재된 것만** 준다. 로컬 DB는 급수대·화장실·인증센터가 비어 있어 로컬에서는 못 본 종류다.
- 종류별 건수를 보고(로컬은 공기주입기 1,100 · 따릉이 3,244 · 사고다발지 111 · 수리소 24), 급수대·화장실·인증센터가 실서버에 있으면 그 종류가 응답에 나오는지 본다
- 관광지(TourAPI)는 `/pois/all`이 아니라 `/tours/nearby`·`/tours/{id}`로 나간다. 관광지 적재(문서상 710건)가 실서버에 있는지 확인한다
- 사고다발지는 **최신 `data_year`만**, 따릉이는 **`is_active`만** 나간다. 실서버에 옛 연도·비활성이 섞여 있어도 응답에는 안 나온다(정상)

### 3-2. 환경변수 ✅ 새로 필요한 것 없음
- `ridely.poi.all-cache-seconds`는 `application.yml` 기본값 300. 바꿀 일이 없으면 손대지 않는다(0이면 캐시 끔)
- 기존 필수 값은 그대로: `CORS_ALLOWED_ORIGINS`, `TOUR_API_KEY`, `KAKAO_REST_API_KEY`, `ORS_API_KEY` 등(`.env.example`)

### 3-3. CORS ⬜
- EC2의 `CORS_ALLOWED_ORIGINS`에 **`https://localhost`(Cordova 앱의 실제 오리진)**가 들어 있어야 한다. `application-prod.yml`이 이 목록을 환경변수로 통째로 덮어써서, 로컬 `application.yml`에 넣은 값은 실서버에 반영되지 않는다(`0918/BACKEND_CHANGES.md` 4장). 이미 앱이 실서버로 동작해 왔다면 되어 있는 값이다
- 이번에 새 헤더·메서드를 추가하지 않았으므로 CORS 설정 변경은 없다

## 4. 배포 후 확인 (로컬로는 못 보는 것) ⬜

CloudFront 도메인은 앱 코드(`android-shell/www/js/api.js`의 `API_BASE`)에 있는 것을 쓴다. 아래 `$BASE`로 표기한다.

```bash
BASE=https://d2ym1ymgumwyg8.cloudfront.net/api/v1

# 1) 살아 있나
curl -s "$BASE/health"

# 2) 헤더: 200, Content-Encoding: gzip, ETag(W/로 시작), Cache-Control(max-age=3600, public)
curl -s -D - -o /dev/null -H "Accept-Encoding: gzip" "$BASE/pois/all"

# 3) 전송 크기(gzip): 로컬 실측 약 108KB(4,479건). 수백 KB면 압축이 꺼진 것
curl -s -H "Accept-Encoding: gzip" -o /dev/null -w "%{size_download} bytes, %{time_total}s\n" "$BASE/pois/all"

# 4) 조건부 요청: 2)에서 받은 ETag를 그대로 넣으면 304
curl -s -D - -o /dev/null -H "Accept-Encoding: gzip" -H 'If-None-Match: <2)의 ETag 값>' "$BASE/pois/all"

# 5) 위치 무전송 계약: 쿼리를 붙여도 응답이 같아야 한다(서버가 무시)
curl -s "$BASE/pois/all" | md5sum
curl -s "$BASE/pois/all?lat=37.5&lng=127.0" | md5sum
```

이 중 **CloudFront가 바꿀 수 있는 것**이 핵심이다.

| 확인 | 왜 | 어긋나면 |
|---|---|---|
| `Content-Encoding: gzip` 유지 | 약 525KB가 압축 안 되고 나가면 모바일 데이터에 부담. 로컬에서 강한 ETag가 Tomcat 압축을 꺼버린 전력이 있어(→ 약한 ETag로 수정) 실서버에서도 실제 헤더로 봐야 한다 | CloudFront 캐시 정책의 압축(`Accept-Encoding`) 설정 확인. 원본이 이미 gzip이면 그대로 통과하는 게 정상 |
| `ETag`·`Cache-Control` 유지, 재요청 시 304 | 304면 본문을 다시 안 받는다 | CloudFront가 `If-None-Match`를 원본에 전달하는지(캐시 정책/원본 요청 정책) 확인 |
| 5)의 두 해시가 같음 | 위치 무전송 계약. 쿼리를 무시해야 한다 | 서버가 쿼리를 쓰고 있다는 뜻 — 배포 중단하고 원인 확인 |
| 응답에 종류 4가지(급수대 등 `ROUTE_FACILITY`, 따릉이, 수리소, 사고다발지)가 있음 | 3-1 | 데이터 적재 확인 |
| 첫 요청 지연 | 캐시가 비어 있으면 DB 조회 4번(로컬 실측 0.14~0.24초). 이후 5분간 캐시 | 크게 느리면 DB 인덱스·건수 확인 |

### 4-1. 낡음의 상한이 CloudFront 때문에 커질 수 있다 ❓
`Cache-Control: max-age=3600, public`이라 **CloudFront가 원본 헤더를 따라 1시간 캐시할 수 있다.** 그러면 (서버 5분) + (CloudFront 1시간) + (앱 WebView의 HTTP 캐시 1시간, 그 위에 앱 저장본 24시간)이 겹쳐, 데이터를 다시 적재한 뒤 사용자에게 새 목록이 가기까지 최악의 경우 꽤 걸린다.
- 적재가 드물어(공공데이터 배치) 문제가 안 된다면 그대로 둔다
- 적재 직후 바로 반영해야 하면: CloudFront 무효화(`/api/v1/pois/all`) + 서버 재시작(또는 5분 대기)
- 이 캐시 정책이 원본 헤더를 따르는지는 CloudFront 콘솔에서 확인(이 저장소에서는 알 수 없다)

## 5. 앱(APK) 쪽 배포 시

- ✅ `API_BASE`는 이미 CloudFront 주소다(로컬 테스트용으로 바꿨던 것은 커밋하지 않았다). **새 APK 빌드 전에 `api.js`의 `API_BASE`가 실서버 주소인지 한 번 더 확인**한다 — localhost·LAN IP가 남아 있으면 실기기에서 전부 실패한다
- ✅ CSP `connect-src`에 CloudFront 도메인이 들어 있다. **API 도메인이 바뀌면**(커스텀 도메인 등) `index.html` CSP의 `connect-src`도 함께 바꿔야 한다
- ✅ CSP `img-src`에 `tong.visitkorea.or.kr`(TourAPI 이미지)를 추가해 뒀다
- 카카오 지도 JS 키의 도메인 화이트리스트는 앱 오리진 `https://localhost`로 등록돼 있고 백엔드가 로컬이든 실서버든 같다. 다른 오리진(예: 모바일 웹 호스팅)에서 앱을 열면 지도가 안 뜬다 — 이 경우 별도로 도메인을 등록해야 한다
- 새 앱은 처음 홈에서 「내 주변 인프라 보기」를 누를 때 `/pois/all`을 한 번 받아 24시간 저장한다. 서버가 죽어도 저장본이 있으면 그걸로 동작하고(`stale` 안내), **저장본이 없는 첫 사용자는 서버가 살아 있어야** 한다

## 6. 위치정보 관련 확인 ❓

- ✅ 앱이 서버로 보내는 「내 주변」 요청은 `GET /pois/all` 하나이고 쿼리스트링이 없다(가짜 서버에서 URL로 확인, `PoiAllTest`가 위치 파라미터를 줘도 응답이 같음을 고정)
- ⬜ 실서버의 **접근 로그·CloudFront 로그**에 `/pois/all` 쿼리스트링이 없는지 샘플로 확인한다(앱이 안 붙이므로 없어야 정상)
- ❓ 접근 로그에는 요청 IP가 남는다. 좌표는 없지만 IP 로그의 보관 여부·기간은 별개 사안이다. 자문 때 이 부분이 범위에 포함되는지 확인한다(자문 결과의 정확한 범위는 원문 확인 필요 — `LOCATION_PRIVACY_ARCHITECTURE.md`)
- ❓ 위치정보 동의 문구(회원가입)가 「라이딩 중」으로 한정돼 있어, 홈에서 현위치를 쓰는 것까지 넓힐지는 결정 대기. 새 앱 배포 전에 정하는 것이 좋다

## 7. 운영 중 보게 될 것

- **서버 캐시는 인스턴스별 메모리**다. EC2가 한 대인 지금은 문제없다. 여러 대가 되면 각자 5분 캐시를 든다(정확성 문제는 아님)
- 서버 재시작(배포)하면 캐시가 비어 첫 요청이 조금 느리다
- **건수 경고**: 전체 조회가 10,000건을 넘으면 서버가 경고 로그를 남긴다(`journalctl -u ridely`에서 검색). 현재 약 4,500건, 응답 약 117B/건. 경고가 뜨면 「전 사용자가 항상 전부 받는」 방식을 다시 설계할 시점이다(`NEARBY_INFRA_LOCAL_PLAN.md`의 「데이터가 늘면」)
- 이 응답은 요청 제한이 없는 공개 API다. 서버 캐시로 DB 부담은 막았지만 대역폭 남용은 막지 않는다. CloudFront 캐시가 1차 방어선이 된다(4-1)

## 8. 롤백

- 백엔드: `deploy.yml`이 기동 실패 시 자동 롤백한다. 수동으로는 EC2의 `/opt/ridely/app.jar.bak`을 되돌리고 `systemctl restart ridely`. 기존 API는 그대로라 되돌려도 옛 앱은 영향이 없다
- 새 앱이 이미 배포됐는데 서버만 롤백하면 `/pois/all`이 404라 「내 주변」이 저장본 없는 사용자에게 안 된다. 앱 배포는 서버 확인 뒤에 한다(2장)

## 9. 이번 작업과 무관하지만 배포 때 같이 볼 기존 상태 ❓

이번 변경에서 생긴 것이 아니라 코드를 읽다가 확인한 것이다. 배포 전에 의도한 상태인지만 확인한다.

- **`/api/v1/poc/**`가 공개(`permitAll`)이고 프로파일로 막혀 있지 않다.** 여기에 데이터 적재(`POST .../ingest`, `.../seed/...`)와 외부 API 호출 PoC가 들어 있어, 실서버에서도 비로그인 호출이 가능한 구조다. 실제 운영 서버에서 열려 있는지는 확인하지 못했다(코드상 그렇다는 것). 운영에서 닫을지 별도 결정이 필요하다
- 장소 검색(`/geo/search`)이 502(`GEO-001`)를 냈던 적이 있고 **원인은 미확정**이다. 카카오 REST 키가 서버 IP를 화이트리스트로 제한하고 있을 가능성이 있어, EC2 IP가 바뀌면(재시작으로 공용 IP가 바뀜, 탄력적 IP 미사용 시) 검색이 막힐 수 있다. 탄력적 IP를 쓰는지, 카카오 키의 허용 IP가 현재 서버와 맞는지 확인한다

## 10. 배포 후 체크리스트

- [ ] 실서버 DB에 종류별 데이터가 있다(3-1)
- [ ] `EC2 CORS_ALLOWED_ORIGINS`에 `https://localhost`가 있다(3-3)
- [ ] main 병합 → `deploy.yml` 성공, `/health` 응답
- [ ] `/pois/all` 200 + gzip + `W/` ETag + Cache-Control(4장 2·3)
- [ ] 같은 ETag로 재요청 시 304(4장 4)
- [ ] 쿼리를 붙여도 응답 동일(4장 5)
- [ ] 실서버 로그에 `/pois/all` 쿼리스트링 없음(6장)
- [ ] 새 APK의 `API_BASE`가 실서버 주소(5장)
- [ ] 새 APK 실기기에서 「내 주변 인프라 보기」 동작(서비스 지역 안에서 — 한강 주변 실제 마커 표시는 아직 미확인)
- [ ] 동의 문구 결정(6장)
- [ ] `/poc/**` 공개 상태 결정, 카카오 REST 키 IP(9장)
