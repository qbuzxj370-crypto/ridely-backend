package kr.ridely.infra.durunubi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GPX 파일 파서 (스파이크 전용).
 *
 * <p>GPX 1.1 구조 중 우리가 보는 부분만 읽는다:
 * <pre>
 *   &lt;gpx&gt;
 *     &lt;wpt lat lon/&gt;            지점 (인증센터 등이 들어올 수 있음)
 *     &lt;trk&gt;
 *       &lt;trkseg&gt;               ★ 구간. 이게 우리가 찾던 경계다
 *         &lt;trkpt lat lon&gt;&lt;ele/&gt;&lt;/trkpt&gt;
 *       &lt;/trkseg&gt;
 *     &lt;/trk&gt;
 *     &lt;rte&gt;&lt;rtept lat lon/&gt;&lt;/rte&gt;   trk 대신 rte를 쓰는 파일도 있어 함께 센다
 *   &lt;/gpx&gt;
 * </pre>
 *
 * <p>네임스페이스를 무시하고 태그명(local name)으로만 찾는다.
 * GPX는 기본 네임스페이스를 선언하는 게 보통인데, 생산자마다 접두사를 달리 붙여서
 * 네임스페이스를 엄격히 따지면 파일마다 파싱이 깨진다. 스파이크에는 과한 정밀도다.
 *
 * <p>거리는 haversine으로 계산한다. PostGIS 없이 자바에서 바로 재야 하고,
 * 우리가 CSV를 분석할 때 쓴 것과 같은 식(R=6371008.8m)이라 수치를 직접 대조할 수 있다.
 */
@Component
public class GpxParser {

    private static final Logger log = LoggerFactory.getLogger(GpxParser.class);

    /** 지구 평균 반지름 (m). CSV 분석과 동일한 값이라야 수치 대조가 성립한다 */
    private static final double EARTH_RADIUS_M = 6371008.8;

    /**
     * GPX 원문을 분석한다.
     *
     * @param xml GPX 파일 본문
     * @return 분석 결과. 파싱 실패도 예외 대신 결과 객체로 돌려준다
     *         (스파이크는 13개 노선 중 몇 개가 실패하는지도 정보다)
     */
    public GpxAnalysis analyze(String xml) {
        if (xml == null || xml.isBlank()) {
            return GpxAnalysis.failed("본문이 비어 있음");
        }
        // GPX가 아니라 HTML 오류 페이지가 내려오는 경우를 먼저 거른다.
        // durunubi.kr은 파일이 없어도 200에 로그인/오류 페이지를 주는 사례가 흔하다.
        String head = xml.stripLeading();
        if (!head.startsWith("<?xml") && !head.startsWith("<gpx")) {
            return GpxAnalysis.failed("GPX가 아닌 응답 (앞부분: "
                    + head.substring(0, Math.min(120, head.length())).replaceAll("\\s+", " ") + ")");
        }

        Document doc;
        try {
            doc = newSecureBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            log.warn("GPX 파싱 실패", e);
            return GpxAnalysis.failed("XML 파싱 실패: " + e.getMessage());
        }

        GpxAnalysis result = new GpxAnalysis();
        result.setParsed(true);
        result.setTrkCount(countByLocalName(doc, "trk"));
        result.setWptCount(countByLocalName(doc, "wpt"));
        result.setRteCount(countByLocalName(doc, "rte"));
        result.setRteptCount(countByLocalName(doc, "rtept"));

        List<Double> segmentLengths = new ArrayList<>();
        List<Double> gaps = new ArrayList<>();
        int trkptTotal = 0;
        int eleTotal = 0;

        NodeList segments = doc.getElementsByTagNameNS("*", "trkseg");
        for (int i = 0; i < segments.getLength(); i++) {
            List<double[]> points = readPoints((Element) segments.item(i), "trkpt");
            trkptTotal += points.size();
            eleTotal += countElevation((Element) segments.item(i));

            double segmentLength = 0;
            for (int p = 0; p + 1 < points.size(); p++) {
                double d = haversineM(points.get(p), points.get(p + 1));
                segmentLength += d;
                gaps.add(d);
            }
            segmentLengths.add(round1(segmentLength / 1000));
        }

        result.setTrksegCount(segments.getLength());
        result.setTrkptCount(trkptTotal);
        result.setElevationPointCount(eleTotal);
        result.setSegmentLengthsKm(segmentLengths);
        result.setTotalLengthKm(round1(segmentLengths.stream().mapToDouble(Double::doubleValue).sum()));

        if (!gaps.isEmpty()) {
            Collections.sort(gaps);
            result.setMaxGapWithinSegmentM(Math.round(gaps.get(gaps.size() - 1)));
            result.setMedianGapWithinSegmentM(Math.round(gaps.get(gaps.size() / 2)));
        }
        return result;
    }

    /**
     * XXE(외부 엔티티 주입)를 차단한 DocumentBuilder.
     * 외부에서 받은 XML을 파싱하므로 기본 설정을 그대로 쓰면 안 된다.
     */
    private DocumentBuilder newSecureBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private int countByLocalName(Document doc, String localName) {
        return doc.getElementsByTagNameNS("*", localName).getLength();
    }

    /** 지정한 태그의 lat/lon 속성을 순서대로 읽는다. 값이 없거나 형식이 틀린 점은 건너뛴다 */
    private List<double[]> readPoints(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<double[]> points = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String lat = e.getAttribute("lat");
            String lon = e.getAttribute("lon");
            if (lat.isBlank() || lon.isBlank()) {
                continue;
            }
            try {
                // 순서를 (경도, 위도)로 둔다. PostGIS·WKT가 경도를 먼저 받으므로
                // 나중에 적재 코드로 옮길 때 뒤집을 일이 없게 처음부터 맞춰 둔다.
                points.add(new double[]{Double.parseDouble(lon), Double.parseDouble(lat)});
            } catch (NumberFormatException ignored) {
                // 좌표가 깨진 점은 세지 않는다
            }
        }
        return points;
    }

    private int countElevation(Element parent) {
        return parent.getElementsByTagNameNS("*", "ele").getLength();
    }

    /** 두 점 사이 대권 거리 (m). a·b는 {경도, 위도} */
    private double haversineM(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[1]);
        double lat2 = Math.toRadians(b[1]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[0] - a[0]);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
