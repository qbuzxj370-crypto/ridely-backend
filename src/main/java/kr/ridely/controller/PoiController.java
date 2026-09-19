package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.poi.PoiAllResponseDTO;
import kr.ridely.dto.poi.PoiNearbyResponseDTO;
import kr.ridely.service.PoiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 자전거 인프라 POI 조회 컨트롤러.
 *
 * 지도 레이어가 종류를 켜고 끌 때마다 부른다. 적재된 데이터를 PostGIS로 검색하므로 외부 API 호출이 없다.
 */
@Tag(name = "인프라 POI", description = "위치 기반 자전거 인프라 조회 (인증 불필요)")
@RestController
@RequestMapping("/api/v1/pois")
@RequiredArgsConstructor
@Validated
@SecurityRequirements   // 비회원도 지도를 볼 수 있어야 하므로 인증을 요구하지 않는다
public class PoiController {

    private final PoiService poiService;

    @Operation(summary = "주변 인프라 POI 조회",
            description = """
                    좌표 반경 내 자전거 인프라를 종류에 상관없이 가까운 순으로 합쳐 반환한다.
                    응답에 조회 조건(center·radiusM)이 함께 담긴다.

                    - `types`: 미지정 시 전체. CSV로 여러 개를 보낼 수 있다
                      `WATER` 급수대 · `TOILET` 화장실 · `CERT_CENTER` 국토종주 인증센터 ·
                      `AIR_PUMP` 공기주입기 · `REPAIR_SHOP` 수리센터 ·
                      `BIKE_STATION` 따릉이 대여소 · `ACCIDENT_ZONE` 사고다발지역
                    - `radiusM`: 기본 1000, 최대 5000

                    **반경 내에 아무것도 없어도 200에 빈 배열을 준다.** 관광지 조회(`/tours/nearby`)가
                    `POI-001`(404)을 내는 것과 다르다. 인프라는 종류마다 밀도 차가 커서
                    0건이 정상 상태다 - 서울 한강 구간에 수리소가 24곳뿐이라 반경 1km로는
                    대부분의 지점에서 0건이 나온다.

                    ⚠️ **희소한 종류는 반경을 넓혀야 한다.** 수리소·사고다발지 레이어를 기본값
                    1000으로 켜면 화면이 거의 비어 보인다. 종류별 적재 건수는 프론트 가이드에 있다.

                    - 서비스 지역(한강 서울 구간) 밖: ROUTE-003
                    - 좌표 범위 초과·형식 오류·필수값 누락: COMMON-001
                    """)
    @GetMapping("/nearby")
    public ApiResponse<PoiNearbyResponseDTO> nearby(
            @RequestParam
            @DecimalMin(value = "33.0", message = "위도는 33.0~39.0 범위여야 합니다")
            @DecimalMax(value = "39.0", message = "위도는 33.0~39.0 범위여야 합니다")
            double lat,

            @RequestParam
            @DecimalMin(value = "124.0", message = "경도는 124.0~132.0 범위여야 합니다")
            @DecimalMax(value = "132.0", message = "경도는 124.0~132.0 범위여야 합니다")
            double lng,

            @RequestParam(defaultValue = "1000")
            @Min(value = 1, message = "반경은 1m 이상이어야 합니다")
            @Max(value = 5000, message = "반경은 5000m 이하여야 합니다")
            int radiusM,

            @RequestParam(required = false)
            String types) {

        return ApiResponse.ok(poiService.findNearby(lat, lng, radiusM, parseTypes(types)));
    }

    /**
     * 캐시 수명. 적재 데이터는 배치로 가끔만 바뀌고, 앱은 이 응답을 폰에 저장해 쓰므로 자주 다시
     * 받을 이유가 없다. 적재 직후 최대 이 시간 동안 옛 목록이 보일 수 있다는 뜻이기도 하다.
     */
    private static final Duration ALL_CACHE_MAX_AGE = Duration.ofHours(1);

    @Operation(summary = "서비스 지역 인프라 POI 전체 조회",
            description = """
                    적재된 인프라 POI를 **전부** 한 번에 돌려준다. 앱이 이걸 폰에 저장해 두고
                    GPS로 「내 주변」을 직접 거른다 — 서버에 내 위치를 묻지 않으려는 구조다.

                    - **파라미터가 없다.** 좌표·반경·격자·「내 근처」 같은 값을 받지 않고, 모든 사용자가 같은 응답을 받는다.
                      위치 단서가 요청에 들어가면 위치정보를 서버로 보내지 않는다는 원칙이 깨지므로 추가하지 말 것
                    - 종류: `WATER` 급수대 · `TOILET` 화장실 · `CERT_CENTER` 인증센터 · `AIR_PUMP` 공기주입기 ·
                      `REPAIR_SHOP` 수리센터 · `BIKE_STATION` 따릉이 대여소 · `ACCIDENT_ZONE` 사고다발지역
                      (시설 4종은 `type`이 `ROUTE_FACILITY`이고 세부 종류가 `facilityType`에 온다)
                    - **응답을 슬림하게 유지한다.** `distanceM`은 없고 사고다발지 폴리곤은 담지 않는다.
                      도형이 필요하면 반경 조회(`/pois/nearby`)나 코스 추천 응답을 쓴다
                    - **캐시**: `Cache-Control: max-age=3600`과 ETag를 준다. `If-None-Match`로 다시 물으면 바뀐 게 없을 때 304다
                    - 결과가 없어도 200에 빈 배열이다(404 아님)

                    ⚠️ 적재가 늘면 모든 사용자의 다운로드가 그만큼 커진다. 약 5,500건일 때 기준이며 커지면 서버 로그에 경고가 남는다.
                    """)
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<PoiAllResponseDTO>> all() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(ALL_CACHE_MAX_AGE).cachePublic())
                .body(ApiResponse.ok(poiService.findAll()));
    }

    /** "WATER,REPAIR_SHOP" → ["WATER", "REPAIR_SHOP"]. 빈 값이면 서비스의 기본값을 쓰도록 빈 목록을 넘긴다 */
    private List<String> parseTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
