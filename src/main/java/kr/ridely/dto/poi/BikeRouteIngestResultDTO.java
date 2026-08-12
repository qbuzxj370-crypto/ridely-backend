package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 국토종주 자전거길 노선 적재 결과.
 * POST /api/v1/poc/seed/bike-routes 응답.
 *
 * 적재가 제대로 됐는지 눈으로 확인하려고 노선별 요약을 함께 담는다.
 * 다음 세 가지가 어긋나면 파싱이나 원본을 의심해야 한다.
 *   - 판별 인코딩이 예상과 다르다
 *   - 건너뛴 행이 늘었다 (원본의 알려진 결손은 2행뿐이다)
 *   - 실측 길이가 공식 거리와 크게 다르다
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BikeRouteIngestResultDTO {

    /**
     * CSV를 읽어낸 인코딩. 원본 배포본은 CP949(MS949)지만
     * 편집기에서 저장하면 UTF-8로 바뀌므로 판별한 값을 남긴다.
     */
    private String charsetName;

    /** CSV 데이터 행 수 (헤더·빈 줄 제외) */
    private int dataRowCount;

    /**
     * 형식이 어긋나 건너뛴 행 수.
     * 원본에 알려진 결손이 2행(순번 38569·38570) 있으므로 0이 정상은 아니다.
     */
    private int skippedRowCount;

    /** CSV에서 읽은 국토종주 노선 수 (코드 1~13 중 데이터가 있는 것) */
    private int routeCount;

    /** 좌표 점 총합 */
    private int totalPointCount;

    /** 실제로 INSERT·UPDATE된 행 수 */
    private int upsertedCount;

    /** 노선별 요약 */
    private List<RouteSummary> routes;

    /** 노선 한 개의 적재 요약 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteSummary {

        /** 노선 코드 (1~13) */
        private int routeCode;

        /** 노선명. 예: "한강종주자전거길" */
        private String routeName;

        /** 이 노선에 사용된 좌표 점 수 */
        private int pointCount;

        /**
         * MultiLineString 파트 수 (좌표 간격 3km 초과 시 분리).
         * 13개 중 5개가 2 이상이다 — 한강종주 3·남한강 9·북한강 5·오천 4·동해안(강원) 4.
         */
        private int partCount;

        /** 저장된 값. 자전거행복나눔 공식 안내 거리(km) */
        private BigDecimal officialLengthKm;

        /**
         * 저장된 형상에서 ST_Length로 계산한 실측 길이(km). <b>검증용이며 저장되지 않는다.</b>
         * 공식값과 크게 다른 노선이 셋 있는데 정상이다 —
         * 한강종주 0.49배(공식이 남한강 포함), 북한강 1.61배, 동해안(경북) 1.62배.
         */
        private BigDecimal geometryLengthKm;
    }
}
