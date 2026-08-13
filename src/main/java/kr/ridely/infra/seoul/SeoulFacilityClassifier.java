package kr.ridely.infra.seoul;

import org.springframework.stereotype.Component;

/**
 * 서울시 자전거 편의시설(tvBicycleEtc) 종류 판정.
 *
 * <p>이 데이터에는 <b>시설 종류를 담는 전용 필드가 없다.</b>
 * {@code THMS_TYPE}·{@code THMS_NM}은 전 3,374건이 동일한 값이라 쓸모가 없다.
 * 그래서 시설명과 상세정보 항목을 조합해 판정한다.
 *
 * <h3>판정 규칙 (2026-08-12 전량 분포 조사로 확정)</h3>
 * <pre>
 *   1. DTL_INFO_VL06(운영형태) 또는 VL07(수리범위)이 채워짐  → REPAIR_SHOP
 *   2. 시설명에 "주입기" 포함                                → AIR_PUMP
 *   3. 그 외                                                → OTHER (적재하지 않음)
 * </pre>
 *
 * <h3>왜 VL08을 쓰지 않는가</h3>
 * 항목명은 "이동수리센터 순회일정"인데, 값 7건 중 3건이
 * {@code 강동구청 교통행정과}·{@code 노원구청 탄소중립도시과} 같은 <b>관리부서명</b>이다.
 * 텍스트가 깨진 게 아니라 <b>들어갈 칸이 틀린 것</b>이다 —
 * 같은 형식의 값이 {@code DTL_INFO_10}에 1,043건 들어 있고, 그쪽이 사실상 관리부서 칸이다
 * (다만 항목명 {@code NM10}이 대부분 비어 있어 자치구가 헷갈린 것으로 보인다).
 *
 * <p>따라서 <b>08에 값이 있다고 이동수리센터인 것이 아니다.</b>
 * 08을 판정에 넣으면 {@code 일반거치대} 2건이 수리센터로 잘못 분류된다(실측 확인).
 *
 * <h3>왜 시설명으로 거르지 않는가</h3>
 * VL06·VL07이 채워졌다는 것은 담당자가 "여기는 수리 기능이 있다"고 입력한 것이다.
 * 시설명은 등록 당시 붙인 라벨일 뿐이고, 거치대 옆에 간이 수리대를 두는 사례도 있다.
 * <b>이름으로 뒤집으면 우리 추측을 원본 입력 위에 두게 되므로 하지 않는다.</b>
 * 오탐 1~2건이 섞이는 편이 진짜 수리소를 빠뜨리는 것보다 낫다 — 전체가 23건뿐이다.
 *
 * <h3>왜 "주입기" 한 단어인가</h3>
 * 이 한 단어로 변형이 전부 잡힌다 — 오타({@code 자잔거 공기주입기}·{@code 잦너거 공기주입기}),
 * 공백 변형({@code 수동식 공기 주입기}), 방식 표기(태양광·자동식·기계식).
 * {@code "공기주입"}으로 잡으면 공백 변형 2건을 놓쳐 1,098건이 된다.
 */
@Component
public class SeoulFacilityClassifier {

    /** 운영형태. 값 대부분이 "상설|"이며 수리센터에만 입력하도록 안내돼 있다 */
    private static final String DETAIL_OPERATION_TYPE = "06";

    /** 수리범위. 값 16건이 전부 수리 문구라 신뢰도가 가장 높다 */
    private static final String DETAIL_REPAIR_SCOPE = "07";

    /** 운영시간. repair_shop.operating_hours로 옮긴다 */
    private static final String DETAIL_OPERATING_HOURS = "01";

    /** 시설명에서 공기주입기를 가려내는 키워드 */
    private static final String AIR_PUMP_KEYWORD = "주입기";

    /** 무료 수리 여부 파생 키워드. "경정비 무상", "공임비 무상", "무상점검" 등 */
    private static final String FREE_KEYWORD = "무상";

    /**
     * 시설 한 건의 종류를 판정한다.
     */
    public SeoulFacilityKind classify(SeoulBicycleEtcResponse.Row row) {
        if (hasText(row.detailValue(DETAIL_OPERATION_TYPE))
                || hasText(row.detailValue(DETAIL_REPAIR_SCOPE))) {
            return SeoulFacilityKind.REPAIR_SHOP;
        }
        String name = row.getContentName();
        if (name != null && name.contains(AIR_PUMP_KEYWORD)) {
            return SeoulFacilityKind.AIR_PUMP;
        }
        return SeoulFacilityKind.OTHER;
    }

    /**
     * 수리 범위에 "무상"이 들어 있으면 무료 수리소로 본다.
     * 자치구 운영 수리센터는 대체로 경정비가 무상이고 부품만 실비다.
     */
    public boolean isFreeRepair(SeoulBicycleEtcResponse.Row row) {
        String scope = row.detailValue(DETAIL_REPAIR_SCOPE);
        return scope != null && scope.contains(FREE_KEYWORD);
    }

    /** 운영시간. 값 형식이 제각각이라(줄바꿈·안내문 혼재) 가공하지 않고 그대로 옮긴다 */
    public String operatingHours(SeoulBicycleEtcResponse.Row row) {
        String hours = row.detailValue(DETAIL_OPERATING_HOURS);
        return hasText(hours) ? hours.trim() : null;
    }

    /**
     * 수리범위(VL07)가 채워졌는지. 검수용 지표다.
     *
     * <p>07이 채워진 건은 수리 문구가 명확해 확실하고, 06만 걸린 건은 상대적으로 덜 확실하다.
     * 적재 결과에 이 구분을 남기면 23건을 눈으로 훑을 때 어디를 먼저 볼지 알 수 있다.
     */
    public boolean hasRepairScope(SeoulBicycleEtcResponse.Row row) {
        return hasText(row.detailValue(DETAIL_REPAIR_SCOPE));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
