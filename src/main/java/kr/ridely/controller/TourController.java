package kr.ridely.controller;

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
 * GET /api/v1/tours/nearby — 좌표 주변 관광지를 가까운 순으로 조회
 *
 * 적재해 둔 데이터를 PostGIS로 검색하므로 요청마다 TourAPI를 호출하지 않는다.
 * 인증 없이 접근 가능 (비회원도 코스를 짜볼 수 있어야 한다).
 */
@RestController
@RequestMapping("/api/v1/tours")
@RequiredArgsConstructor
@Validated
public class TourController {

    private final TourService tourService;

    /**
     * 주변 관광지 조회.
     *
     * @param lat            위도 (한반도 범위)
     * @param lng            경도 (한반도 범위)
     * @param radiusM        반경 (m). 최대 5000 — API 명세 기준
     * @param contentTypeIds 관광타입 CSV. 미지정 시 12,14,39 (관광지·문화시설·음식점)
     */
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
