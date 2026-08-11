package kr.ridely.infra.durunubi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * S2 형상 리포트 — GPX가 구간 경계를 담고 있는가, 우리 휴리스틱과 맞는가.
 *
 * <p>판정 기준은 단 하나다. <b>gpxTrksegTotal이 csvPartsAt3km와 일치하는가.</b>
 * <ul>
 *   <li>대체로 일치 → 휴리스틱이 원본과 같은 답을 냈다는 뜻.
 *       GPX를 정본으로 삼고 CSV·임계값·제안서를 폐기한다</li>
 *   <li>trkseg가 전부 1 → GPX도 구간 정보를 담고 있지 않다.
 *       두루누비를 써도 휴리스틱이 여전히 필요하므로 채택 이유가 절반으로 준다</li>
 *   <li>크게 어긋남 → 두 소스가 다른 형상을 담고 있다는 뜻.
 *       어느 쪽이 맞는지 별도 판단이 필요하므로 스파이크를 연장하지 말고 보고한다</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DurunubiGeometryReport {

    /** 사람이 바로 읽는 종합 판정 */
    private String verdict;

    /** GPX 다운로드 시도 수 / 성공 수 */
    private int gpxAttempted;
    private int gpxSucceeded;

    private List<RouteGeometry> routes = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteGeometry {

        private int routeCode;
        private String officialName;
        private String routeIdx;
        private String themeNm;

        /** 이 길에 속한 코스 수. 두루누비가 노선을 몇 조각으로 나눠 제공하는지 */
        private int courseCount;

        /** 코스별 crsDstnc 합 (km) — 두루누비가 말하는 공식 거리 */
        private double courseDistanceSumKm;

        /** GPX trkseg 총합 — ★ 원본이 명시한 구간 수 */
        private int gpxTrksegTotal;

        /** GPX 좌표로 계산한 길이 합 (km) */
        private double gpxLengthTotalKm;

        /** 우리 CSV를 3km로 분리했을 때의 구간 수 */
        private int csvPartsAt3km;

        /** 우리 CSV를 3km로 분리했을 때의 길이 (km) */
        private double csvLengthKm;

        /** 자전거행복나눔 공식 안내 거리 (km) */
        private int officialLengthKm;

        /** 이 노선 한 줄 판정 */
        private String note;

        private List<Course> courses = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Course {

        private String crsIdx;
        private String crsKorNm;

        /** 코스 길이 (km) — 공식값 */
        private String crsDstnc;

        /** 총 소요시간 (분) */
        private String crsTotlRqrmHour;

        /** 난이도 1=하 2=중 3=상 — W3 파이프라인 강도 라벨 재료 */
        private String crsLevel;

        /** 순환형태 */
        private String crsCycle;

        /** 행정구역 */
        private String sigun;

        private String gpxPath;

        /** GPX 분석 결과 */
        private GpxAnalysis gpx;
    }
}
