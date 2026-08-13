package kr.ridely.infra.seoul;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서울시 편의시설 분류 규칙 단위 테스트.
 *
 * 판정 근거는 2026-08-12 전량(3,374건) 분포 조사다.
 * 여기 담은 값들은 전부 실제 원본에서 관측된 것이라 임의로 바꾸면 안 된다.
 */
class SeoulFacilityClassifierTest {

    private final SeoulFacilityClassifier classifier = new SeoulFacilityClassifier();

    /** 테스트는 같은 패키지라 @JsonAnySetter 메서드로 상세정보를 채울 수 있다 */
    private SeoulBicycleEtcResponse.Row row(String contentName, String detailNo, String detailValue) {
        SeoulBicycleEtcResponse.Row row = new SeoulBicycleEtcResponse.Row();
        row.setContentName(contentName);
        if (detailNo != null) {
            row.collectUnmappedField("DTL_INFO_VL" + detailNo, detailValue);
        }
        return row;
    }

    @Test
    @DisplayName("수리범위(VL07)가 채워지면 수리센터다 — 신뢰도가 가장 높은 신호")
    void repairScopeMeansRepairShop() {
        SeoulBicycleEtcResponse.Row r =
                row("도림천자전거수리센터", "07", "생활용 자전거 전문정비(고급자전거 제외)");

        assertThat(classifier.classify(r)).isEqualTo(SeoulFacilityKind.REPAIR_SHOP);
        assertThat(classifier.hasRepairScope(r)).isTrue();
    }

    @Test
    @DisplayName("운영형태(VL06)만 채워져도 수리센터다")
    void operationTypeAloneMeansRepairShop() {
        SeoulBicycleEtcResponse.Row r = row("광진구 자전거종합서비스센터", "06", "상설|");

        assertThat(classifier.classify(r)).isEqualTo(SeoulFacilityKind.REPAIR_SHOP);
        // 07이 비었으므로 검수 대상으로 표시된다
        assertThat(classifier.hasRepairScope(r)).isFalse();
    }

    @Test
    @DisplayName("★ 순회일정(VL08)에 부서명이 들어와도 수리센터로 보지 않는다")
    void departmentNameInSlot08IsNotRepairShop() {
        // 실제 원본: VL08 값 7건 중 3건이 관리부서명이다. 항목명은 "이동수리센터 순회일정"인데
        // 같은 형식의 값이 DTL_INFO_10(사실상 관리부서 칸)에 1,043건 있다 — 칸을 잘못 쓴 것이다.
        // 08을 판정에 넣으면 이 "일반거치대" 2건이 수리센터로 잘못 분류된다.
        SeoulBicycleEtcResponse.Row r = row("일반거치대", "08", "강동구청 교통행정과");

        assertThat(classifier.classify(r)).isEqualTo(SeoulFacilityKind.OTHER);
    }

    @Test
    @DisplayName("시설명이 거치대여도 VL06/07이 있으면 수리센터다 — 이름으로 뒤집지 않는다")
    void nameDoesNotOverrideOperatorInput() {
        // VL06/07을 채웠다는 건 담당자가 "수리 기능이 있다"고 입력한 것이다.
        // 거치대 옆에 간이 수리대를 두는 사례가 있으므로 이름으로 배제하지 않는다.
        SeoulBicycleEtcResponse.Row r = row("자전거 일반거치대", "07", "경정비 무상, 부품교체 실비");

        assertThat(classifier.classify(r)).isEqualTo(SeoulFacilityKind.REPAIR_SHOP);
    }

    @ParameterizedTest
    @DisplayName("시설명에 '주입기'가 있으면 공기주입기다 — 오타·공백·방식 변형을 모두 흡수한다")
    @ValueSource(strings = {
            "자전거 공기주입기",
            "공기주입기",
            "자전거공기주입기",
            "자전거 공기주입기(자동)",
            "태양광 공기주입기",
            "수동식 공기 주입기",     // 공백 변형 — "공기주입"으로 잡으면 놓친다
            "자잔거 공기주입기",       // 원본 오타
            "잦너거 공기주입기",       // 원본 오타
            "갈현로7길25 공기주입기"   // 위치명 + 키워드
    })
    void airPumpByNameKeyword(String name) {
        assertThat(classifier.classify(row(name, null, null)))
                .isEqualTo(SeoulFacilityKind.AIR_PUMP);
    }

    @ParameterizedTest
    @DisplayName("거치대·보관대는 적재 대상이 아니다 — bike_parking 소스는 행안부 API다")
    @ValueSource(strings = {
            "자전거 일반거치대", "자전거거치대", "일반거치대", "자전거보관대", "강남구청역 1번출구"
    })
    void rackAndUnknownAreOther(String name) {
        assertThat(classifier.classify(row(name, null, null)))
                .isEqualTo(SeoulFacilityKind.OTHER);
    }

    @Test
    @DisplayName("수리범위에 '무상'이 있으면 무료 수리소로 본다")
    void derivesFreeFromRepairScope() {
        assertThat(classifier.isFreeRepair(row("A", "07", "경정비 무상, 부품교체 실비"))).isTrue();
        assertThat(classifier.isFreeRepair(row("B", "07", "공임비 무상, 부품교체 실비"))).isTrue();
        assertThat(classifier.isFreeRepair(row("C", "07", "경정비, 부품교체, 세척 등"))).isFalse();
        assertThat(classifier.isFreeRepair(row("D", "06", "상설|"))).isFalse();
    }

    @Test
    @DisplayName("운영시간은 가공하지 않고 그대로 옮긴다 — 값 형식이 제각각이다")
    void operatingHoursPassedThrough() {
        String raw = "운영시간_화요일~일요일(09:00~18:00)  월요일 휴무";

        assertThat(classifier.operatingHours(row("A", "01", raw))).isEqualTo(raw.trim());
        assertThat(classifier.operatingHours(row("B", "01", "   "))).isNull();
        assertThat(classifier.operatingHours(row("C", null, null))).isNull();
    }
}
