package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.tour.TourNearbyResponseDTO;
import kr.ridely.service.TourService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 관광 콘텐츠 조회 컨트롤러. ★ 공모전 핵심 데이터
 *
 * 적재해 둔 데이터를 PostGIS로 검색하므로 요청마다 TourAPI를 호출하지 않는다.
 * 개발계정 트래픽이 오퍼레이션당 일 1,000건이라 실시간 호출은 불가능하다.
 */
@Tag(name = "관광지", description = "위치 기반 관광 콘텐츠 조회 (인증 불필요)")
@RestController
@RequestMapping("/api/v1/tours")
@RequiredArgsConstructor
@Validated
@SecurityRequirements   // 비회원도 코스를 짜볼 수 있어야 하므로 인증을 요구하지 않는다
public class TourController {

    private final TourService tourService;

    @Operation(summary = "주변 관광지 조회",
            description = """
                    좌표 반경 내 관광지를 가까운 순으로 반환한다. 응답에 조회 조건(center·radiusM)이
                    함께 담기므로, 지도 중심과 반경 원을 다시 그릴 때 요청값을 따로 보관하지 않아도 된다.

                    적재해 둔 데이터를 검색하므로 응답이 빠르고 외부 API 호출이 없다.
                    현재 적재 범위는 한강 서울 구간(아라한강갑문~잠실)이다.

                    - `contentTypeIds`: 미지정 시 12,14,39 전체. 예) `12` (관광지만), `12,14`
                    - `radiusM`: 기본 1000, 최대 5000
                    - 반경 내 결과 없음: POI-001 (404)
                    - 좌표 범위 초과·형식 오류·필수값 누락: COMMON-001
                    """)
    @GetMapping("/nearby")
    public ApiResponse<TourNearbyResponseDTO> nearby(
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
            String contentTypeIds) {

        return ApiResponse.ok(
                tourService.findNearby(lat, lng, radiusM, parseContentTypeIds(contentTypeIds)));
    }

    /** "12,14" → ["12", "14"]. 빈 값이면 서비스의 기본값을 쓰도록 빈 목록을 넘긴다 */
    private List<String> parseContentTypeIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
