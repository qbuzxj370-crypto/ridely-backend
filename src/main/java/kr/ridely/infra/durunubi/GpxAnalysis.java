package kr.ridely.infra.durunubi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * GPX 한 파일의 형상 분석 결과.
 *
 * ★ 스파이크의 핵심 산출물이다.
 *
 * <p>우리가 임계값(3km/5km) 논쟁을 한 이유는 행안부 CSV가 <b>구간 경계를 잃어버렸기</b> 때문이다.
 * GPX는 {@code <trkseg>}로 구간을 원본에 명시한다. 따라서 확인할 것은 단 하나다 —
 * <b>trksegCount가 우리가 거리 휴리스틱으로 추정한 구간 수와 맞는가.</b>
 *
 * <p>맞으면 휴리스틱을 버리고 GPX를 정본으로 삼는다.
 * trksegCount가 전부 1이면 GPX도 구간 정보를 담고 있지 않다는 뜻이므로 채택 근거가 사라진다.
 *
 * <p>maxGapM을 함께 재는 이유: trkseg가 1개인데 큰 점프가 있으면
 * "GPX도 CSV와 같은 문제를 갖고 있다"는 뜻이고, 그 경우 휴리스틱을 그대로 써야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GpxAnalysis {

    /** 다운로드·파싱 성공 여부. 실패 시 나머지 값은 의미 없다 */
    private boolean parsed;

    /** 실패 사유 (성공이면 null) */
    private String failReason;

    /** &lt;trk&gt; 개수 */
    private int trkCount;

    /** ★ &lt;trkseg&gt; 개수 = 원본이 명시한 구간 수 */
    private int trksegCount;

    /** &lt;trkpt&gt; 총 개수 */
    private int trkptCount;

    /** &lt;wpt&gt; 개수. 인증센터·시설이 들어 있을 수 있어 함께 센다 */
    private int wptCount;

    /** &lt;rte&gt;/&lt;rtept&gt; 개수. trk 대신 rte를 쓰는 GPX도 있다 */
    private int rteCount;
    private int rteptCount;

    /** 고도(ele) 값이 있는 trkpt 개수. ORS 없이 상승고도를 낼 수 있는지 판단용 */
    private int elevationPointCount;

    /** 구간별 길이 합 (km). 구간 사이 점프는 더하지 않는다 */
    private double totalLengthKm;

    /** 구간 내부의 최대 인접 좌표 간격 (m). 구간 안에 숨은 점프가 있는지 본다 */
    private double maxGapWithinSegmentM;

    /** 구간 내부 인접 좌표 간격의 중앙값 (m). CSV(전체 중앙값 25m)와 밀도를 비교한다 */
    private double medianGapWithinSegmentM;

    /** 구간별 길이 (km). 우리 CSV 분석의 구간별 길이와 직접 대조한다 */
    private List<Double> segmentLengthsKm = new ArrayList<>();

    public static GpxAnalysis failed(String reason) {
        GpxAnalysis analysis = new GpxAnalysis();
        analysis.parsed = false;
        analysis.failReason = reason;
        return analysis;
    }
}
