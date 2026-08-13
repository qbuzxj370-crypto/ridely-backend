package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 서울시 자전거 편의시설 적재 결과.
 * POST /api/v1/poc/seoul/bicycle-etc/ingest 응답.
 *
 * 다음이 어긋나면 분류 규칙이나 원본을 의심해야 한다.
 *   - 수집 건수가 3,374에서 크게 벗어난다
 *   - 공기주입기가 1,100에서 벗어난다 (시설명 표기가 또 바뀌었을 수 있다)
 *   - 수리센터가 23에서 벗어난다
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeoulFacilityIngestResultDTO {

    /** API에서 수집한 총 건수. 2026-08-12 기준 3,374 */
    private int fetchedCount;

    /** 종류별 판정 결과 (AIR_PUMP / REPAIR_SHOP / OTHER) */
    private Map<String, Integer> classifiedCount;

    /** route_facility에 새로 INSERT된 공기주입기 수. 재실행하면 0이 정상 */
    private int airPumpInserted;

    /** repair_shop에 새로 INSERT된 수리센터 수. 재실행하면 0이 정상 */
    private int repairShopInserted;

    /** repair_shop 전체 건수 */
    private int repairShopTotal;

    /** 무료 여부별 건수. 수리범위에 "무상"이 있으면 무료로 파생한다 */
    private Map<String, Integer> repairShopByFree;

    /**
     * 수리센터 검수 목록.
     *
     * 23건뿐이라 전부 담는다. 지도에 그려보기 전에 눈으로 훑으라고 두는 것이다.
     * {@code hasRepairScope}가 false인 건(운영형태만 걸린 건)을 먼저 보면 된다.
     */
    private List<RepairShopSummary> repairShops = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepairShopSummary {

        private String shopName;
        private String addr;

        /**
         * 수리범위(DTL_INFO_07)가 채워졌는지.
         * true면 수리 문구가 명확해 확실하고, false면 운영형태(06)만 보고 판정한 것이라 덜 확실하다.
         */
        private boolean hasRepairScope;

        /** 수리범위 원문. 판정 근거를 그대로 보여준다 */
        private String repairScope;

        private boolean free;
        private String operatingHours;
    }
}
