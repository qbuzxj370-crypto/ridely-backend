package kr.ridely.infra.seed;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 국토종주 자전거길 노선 좌표 CSV 읽기.
 *
 * 원본: 행정안전부 자전거길 DB (data.go.kr/data/3038533)
 *       "★국토종주 자전거길 노선좌표.csv" → db/seed/에 배치
 *
 * 형식:
 *   순서,국토종주 자전거길,위도(LINE_XP),경도(LINE_YP)
 *   1,1,37.55737548,126.603468
 *
 * ⚠️ 주의:
 *   - <b>3번째 열이 위도, 4번째 열이 경도다.</b> 컬럼명이 LINE_XP(위도)/LINE_YP(경도)로
 *     통상적인 X=경도, Y=위도와 반대로 붙어 있어 헷갈리기 쉽다
 *   - 노선 코드는 1~46이지만 국토종주 13길은 1~13이다. 14 이상은 지역 자전거길이며
 *     "현재 노선변경으로 사용하지 않음"인 코드도 섞여 있다
 *   - 원본에 <b>좌표가 깨진 행이 있다.</b> 순번 38569·38570은 위도 자리에
 *     문자열("5, latit")이 들어 있고 따옴표 안 콤마 때문에 열이 5개가 된다.
 *     열 수와 숫자 변환을 모두 검사해 걸러낸다
 *
 * <h3>인코딩</h3>
 * 원본 배포본은 CP949지만, 편집기에서 열어 저장하면 UTF-8로 바뀐다(실제로 겪었다).
 * 파일마다 다를 수 있으므로 <b>고정하지 않고 판별한다.</b>
 *
 * <p>판별 순서가 중요하다. CP949는 거의 모든 바이트열을 오류 없이 디코드해버려서
 * 먼저 시도하면 UTF-8 파일도 "성공"한다 — 깨진 채로. 반대로 UTF-8 엄격 디코딩은
 * CP949 한글에서 반드시 실패한다. 그래서 <b>UTF-8을 먼저</b> 시도한다.
 *
 * <p>{@code Files.readAllLines}는 디코딩 오류에 예외를 던진다(REPORT 모드).
 * {@code new String(bytes, charset)}은 조용히 U+FFFD로 대체하므로 판별에 쓰면 안 된다.
 */
@Component
public class BikeRouteCsvReader {

    private static final Logger log = LoggerFactory.getLogger(BikeRouteCsvReader.class);

    /**
     * 인코딩 후보. 순서를 바꾸면 안 된다 — 클래스 주석의 판별 순서 설명 참조.
     */
    private static final List<Charset> CANDIDATE_CHARSETS =
            List.of(StandardCharsets.UTF_8, Charset.forName("MS949"));

    /** 국토종주 자전거길 노선 코드 범위 (14 이상은 지역 자전거길) */
    private static final int MIN_ROUTE_CODE = 1;
    private static final int MAX_ROUTE_CODE = 13;

    /** 컬럼 위치 */
    private static final int COL_SEQUENCE = 0;
    private static final int COL_ROUTE_CODE = 1;
    private static final int COL_LATITUDE = 2;
    private static final int COL_LONGITUDE = 3;
    private static final int COLUMN_COUNT = 4;

    /**
     * 허용 스킵 비율. 원본의 알려진 결손은 2/53,416 = 0.004%다.
     * 이 값을 넘으면 파일이 바뀌었거나 형식이 달라진 것이므로 조용히 넘기지 않는다.
     */
    private static final double MAX_SKIP_RATIO = 0.01;

    /**
     * 비율과 별개로 항상 허용하는 스킵 수.
     * 행이 몇 개뿐인 파일에서는 1%가 0행이 되어 결손 한 개에도 실패한다.
     * 원본 규모(5만여 행)에서는 비율(534행)이 훨씬 크므로 이 값이 영향을 주지 않는다.
     */
    private static final int SKIP_ALLOWANCE_FLOOR = 5;

    /**
     * UTF-8 BOM. 헤더는 어차피 건너뛰지만 남겨두면 진단이 헷갈린다.
     * 이스케이프로 쓴다 — 문자를 그대로 넣으면 소스에 보이지 않는 바이트가 박힌다.
     */
    private static final char BOM = '\uFEFF';

    private final SeedFileProperties properties;

    public BikeRouteCsvReader(SeedFileProperties properties) {
        this.properties = properties;
    }

    /**
     * 국토종주 13개 노선의 좌표를 노선 코드별로 읽는다.
     *
     * @return 판별 인코딩·행 통계와 함께 노선 코드 → 순서대로 정렬된 좌표 목록
     */
    public ParseResult readRouteCoordinates() {
        Path path = Path.of(properties.bikeRouteCoords());
        if (!Files.exists(path)) {
            // 저장소에 없는 파일이라 "왜 없는지"를 로그로 남겨야 원인을 찾을 수 있다
            log.error("노선 좌표 CSV가 없다: {} (공공데이터포털에서 내려받아 배치한다. docs/shared/DATA_SOURCES.md 참조)",
                    path.toAbsolutePath());
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        DecodedFile decoded = decode(path);

        // 정렬 전이므로 순서 값을 함께 들고 있다가 마지막에 정렬한다
        Map<Integer, List<SequencedCoordinate>> collected = new TreeMap<>();
        int dataRows = 0;
        int skipped = 0;

        int otherRoutes = 0;
        List<String> lines = decoded.getLines();
        for (int i = 1; i < lines.size(); i++) {   // 0번은 헤더
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;                          // 빈 줄은 결손이 아니다
            }
            dataRows++;

            SequencedCoordinate parsed = parseLine(line);
            if (parsed == null) {
                skipped++;                         // 형식 오류 = 결손. 비율 검사 대상
                continue;
            }
            // 범위 밖(지역 자전거길)은 결손이 아니라 정상 필터다. 원본의 8% 가까이 되므로
            // 결손과 같이 세면 스킵 비율 검사에 그대로 걸린다.
            if (parsed.getRouteCode() < MIN_ROUTE_CODE || parsed.getRouteCode() > MAX_ROUTE_CODE) {
                otherRoutes++;
                continue;
            }
            collected.computeIfAbsent(parsed.getRouteCode(), key -> new ArrayList<>()).add(parsed);
        }

        verifySkipRatio(path, dataRows, skipped, decoded.getCharset());

        Map<Integer, List<Coordinate>> result = new LinkedHashMap<>();
        collected.forEach((routeCode, points) -> {
            points.sort(Comparator.comparingInt(SequencedCoordinate::getSequence));
            result.put(routeCode, points.stream().map(SequencedCoordinate::getCoordinate).toList());
        });

        log.info("노선 좌표 CSV 읽기 완료: 인코딩 {}, 데이터 {}행, 국토종주 노선 {}개, "
                        + "지역 자전거길 {}행 제외, 형식 오류 {}행 건너뜀",
                decoded.getCharset().name(), dataRows, result.size(), otherRoutes, skipped);
        return new ParseResult(decoded.getCharset().name(), dataRows, skipped, result);
    }

    /**
     * 후보 인코딩을 차례로 시도해 성공한 것을 쓴다.
     * 전부 실패하면 파일이 우리가 아는 형식이 아니므로 진행하지 않는다.
     */
    private DecodedFile decode(Path path) {
        for (Charset charset : CANDIDATE_CHARSETS) {
            try {
                List<String> lines = Files.readAllLines(path, charset);
                if (!lines.isEmpty()) {
                    lines.set(0, stripBom(lines.get(0)));
                }
                return new DecodedFile(charset, lines);
            } catch (CharacterCodingException e) {
                log.debug("{} 로는 디코딩 실패, 다음 후보 시도", charset.name());
            } catch (IOException e) {
                log.error("노선 좌표 CSV 읽기 실패: {}", path.toAbsolutePath(), e);
                throw new BusinessException(ErrorCode.COMMON_500);
            }
        }
        log.error("노선 좌표 CSV 인코딩을 판별하지 못했다: {} (시도: {})",
                path.toAbsolutePath(), CANDIDATE_CHARSETS);
        throw new BusinessException(ErrorCode.COMMON_500);
    }

    /**
     * 스킵 비율이 정상 범위인지 본다.
     *
     * 인코딩을 잘못 골랐거나 파일 형식이 바뀌면 스킵이 폭증한다.
     * 그대로 진행하면 "노선은 적재됐는데 좌표가 절반"인 상태를 못 알아챈다.
     */
    private void verifySkipRatio(Path path, int dataRows, int skipped, Charset charset) {
        if (dataRows == 0) {
            log.error("노선 좌표 CSV에 데이터 행이 없다: {}", path.toAbsolutePath());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        int allowed = Math.max(SKIP_ALLOWANCE_FLOOR, (int) (dataRows * MAX_SKIP_RATIO));
        if (skipped > allowed) {
            double ratio = (double) skipped / dataRows;
            log.error("노선 좌표 CSV 스킵이 과다하다: {}/{}행 ({}%, 허용 {}행), 판별 인코딩 {}. "
                            + "파일 형식이 바뀌었거나 다른 파일을 가리키고 있을 수 있다",
                    skipped, dataRows, Math.round(ratio * 1000) / 10.0, allowed, charset.name());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /**
     * 한 줄을 좌표로 변환한다. <b>형식만 본다.</b>
     * 노선 코드 범위 판정은 호출부에서 한다 — 결손과 정상 필터를 구분해야 하기 때문이다.
     *
     * 열 수를 {@code !=}로 검사하는 이유: 원본에 따옴표 안 콤마가 든 결손 행이 있어
     * 열이 5개가 된다. {@code <}로 두면 그 행이 통과해 엉뚱한 열을 좌표로 읽으려 한다.
     *
     * @return 형식이 어긋나면 null
     */
    private SequencedCoordinate parseLine(String line) {
        String[] columns = line.split(",");
        if (columns.length != COLUMN_COUNT) {
            return null;
        }
        try {
            return new SequencedCoordinate(
                    Integer.parseInt(columns[COL_ROUTE_CODE].trim()),
                    Integer.parseInt(columns[COL_SEQUENCE].trim()),
                    new Coordinate(
                            Double.parseDouble(columns[COL_LONGITUDE].trim()),
                            Double.parseDouble(columns[COL_LATITUDE].trim())
                    ));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stripBom(String first) {
        return (!first.isEmpty() && first.charAt(0) == BOM) ? first.substring(1) : first;
    }

    /** 디코딩 결과 (내부 전달용) */
    @Getter
    @AllArgsConstructor
    private static class DecodedFile {
        private final Charset charset;
        private final List<String> lines;
    }

    /**
     * CSV 파싱 결과.
     *
     * 인코딩과 스킵 수를 함께 돌려주는 이유: 적재 응답에 실어 두면
     * 파일이 바뀌었을 때 DB를 열어보지 않고도 바로 드러난다.
     */
    @Getter
    @AllArgsConstructor
    public static class ParseResult {
        private final String charsetName;
        private final int dataRowCount;
        private final int skippedRowCount;
        private final Map<Integer, List<Coordinate>> coordinatesByRoute;
    }

    /** 정렬용 순서를 달고 있는 중간 표현. 정렬이 끝나면 버린다 */
    @Getter
    @AllArgsConstructor
    private static class SequencedCoordinate {
        private final int routeCode;
        private final int sequence;
        private final Coordinate coordinate;
    }

    /**
     * 좌표 한 점.
     *
     * 필드 순서를 (경도, 위도)로 둔 이유: PostGIS의 ST_MakePoint·WKT가 모두
     * 경도를 먼저 받는다. 원본 CSV는 위도가 먼저라 읽는 시점에 뒤집는다.
     */
    @Getter
    @AllArgsConstructor
    public static class Coordinate {
        private final double lng;
        private final double lat;
    }
}
