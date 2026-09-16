package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.geo.GeoSearchResponseDTO;
import kr.ridely.service.GeoSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 검색 컨트롤러.
 *
 * 출발지를 이름으로 찾게 한다. 사용자가 좌표를 알 리 없고, 지도를 눌러 찍는 것만으로는 「한강공원 어디쯤」을 정확히 집기 어렵다.
 */
@Tag(name = "장소 검색", description = "장소명·주소를 좌표로 변환 (인증 불필요)")
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Validated
@SecurityRequirements   // 비회원도 출발지를 정할 수 있어야 한다
public class GeoController {

    private final GeoSearchService geoSearchService;

    @Operation(summary = "장소 검색",
            description = """
                    장소명이나 주소로 좌표를 찾는다. 결과에서 하나를 고르면 `lat`·`lng`를
                    그대로 코스 추천 요청의 출발지·도착지에 실으면 된다.

                    **서비스 지역 밖도 결과에 나온다.** 목록에서 빼지 않고 `inServiceArea: false`로
                    표시한다. 조용히 사라지면 사용자가 검색어를 잘못 썼다고 생각하고 계속 다시 친다.
                    **화면은 이 값이 false인 항목을 선택 불가로 두고 이유를 보여 줘야 한다.**

                    **결과가 없으면 200에 빈 배열이다.** 오타나 없는 장소를 친 것은 실패가 아니다.
                    외부 검색 서비스가 실제로 응답하지 못했을 때만 `GEO-001`(502)이 나간다 -
                    사용자가 다시 쳐야 하는 상황과 잠시 뒤 재시도해야 하는 상황이 갈린다.

                    - 검색어 누락·길이 초과: COMMON-001
                    - 외부 검색 서비스 실패: GEO-001 (502)
                    """)
    @GetMapping("/search")
    public ApiResponse<GeoSearchResponseDTO> search(
            @RequestParam
            @NotBlank(message = "검색어를 입력해 주세요")
            @Size(max = 100, message = "검색어는 100자 이하여야 합니다")
            String query) {

        return ApiResponse.ok(geoSearchService.search(query.trim()));
    }
}
