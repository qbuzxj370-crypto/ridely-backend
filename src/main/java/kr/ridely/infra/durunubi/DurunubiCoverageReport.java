package kr.ridely.infra.durunubi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S1 커버리지 리포트 — 두루누비에 국토종주 자전거길 코스가 있는가.
 *
 * <p><b>이것이 스파이크의 중단 게이트다.</b>
 *
 * <p>1차 설계는 {@code routeList}로 길 목록을 받아 판정하려 했으나 실측에서 폐기했다.
 * <ul>
 *   <li>{@code routeList}는 brdDiv를 빼면 오류, DNWW를 줘도 <b>0건</b>이다 — 사실상 비어 있다</li>
 *   <li>{@code courseList}의 {@code brdDiv} 필터는 <b>동작하지 않는다.</b>
 *       DNBW를 요청해도 {@code brdDiv:"DNWW"}인 남파랑길 코스가 돌아온다</li>
 * </ul>
 *
 * <p>그래서 필터를 믿지 않고 <b>코스를 전량 받아 응답의 brdDiv 값을 직접 센다.</b>
 * 이름 매칭도 {@code themeNm}이 아니라 {@code crsKorNm}에 적용한다.
 *
 * <p>오탐 방지: 이름 매칭은 <b>DNBW 코스에만</b> 적용한다.
 * 걷기 코스에도 "섬진강"·"금강" 같은 토큰이 들어갈 수 있어 함께 훑으면 없는 커버리지를 만들어낸다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DurunubiCoverageReport {

    /** 두루누비 코스 총 개수 (필터 없이 전량) */
    private int totalCourseCount;

    /** ★ 응답의 brdDiv 값 분포. DNBW가 0이면 자전거 데이터가 없는 것이다 */
    private Map<String, Integer> brdDivDistribution = new LinkedHashMap<>();

    /** 자전거 코스(DNBW) 개수 */
    private int bikeCourseCount;

    /** 국토종주 13길 중 매칭된 개수 */
    private int matchedCount;

    /** 사람이 바로 읽는 판정 문구 */
    private String verdict;

    /** 매칭된 자전거 코스 */
    private List<MatchedCourse> matched = new ArrayList<>();

    /** 매칭되지 않은 국토종주 노선명 */
    private List<String> missing = new ArrayList<>();

    /** 자전거 코스 이름 전체 (자동 매칭이 빗나갔는지 눈으로 확인용) */
    private List<String> bikeCourseNames = new ArrayList<>();

    /** 전체 코스 이름 표본. DNBW가 0일 때 두루누비가 뭘 담고 있는지 보려고 남긴다 */
    private List<String> sampleCourseNames = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedCourse {

        /** 행안부 CSV의 노선 코드 (1~13) */
        private int routeCode;

        /** 코드북 기준 이름 */
        private String officialName;

        private String routeIdx;
        private String crsIdx;

        /** 두루누비가 부르는 코스명 */
        private String crsKorNm;

        /** 코스 길이 (km, 공식값) */
        private String crsDstnc;

        private String brdDiv;
        private String gpxPath;
    }
}
