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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 국토종주 자전거길 주변시설 CSV 읽기.
 *
 * 원본: 행정안전부 자전거길 DB (data.go.kr/data/3038533)
 *       "★국토종주자전거길 주변시설 좌표정보.csv" → db/seed/에 배치
 *
 * 형식:
 *   구분,이름,경도,위도
 *   인증센터,영산하구둑인증센터,126.4457409,34.8019679
 *
 * ⚠️ 주의:
 *   - <b>노선좌표 CSV와 열 순서가 반대다.</b> 이 파일은 3열이 경도, 4열이 위도다.
 *     노선좌표는 3열이 위도, 4열이 경도였다. 같은 배포본인데 다르다
 *   - <b>노선 코드가 없다.</b> 시설이 어느 노선에 속하는지 파일만으로는 알 수 없어
 *     적재 후 좌표로 매칭해야 한다 (RouteFacilityIngestServiceImpl)
 *   - 원본에 완전 중복 109건이 있다. (구분, 이름, 좌표)가 모두 같은 행이다
 *   - 인코딩은 고정하지 않고 판별한다. 이유는 BikeRouteCsvReader 클래스 주석 참조
 *
 * <h3>구분값 검증</h3>
 * 이 파일은 노선좌표와 달리 <b>데이터에 한글이 들어 있다.</b>
 * 인코딩을 잘못 고르면 깨진 문자열이 그대로 DB에 들어가는데, 파싱은 성공하므로
 * 아무도 눈치채지 못한다. 그래서 구분값이 알려진 4종과 맞는지 비율로 검사한다 —
 * <b>인코딩 판별이 실패했는지를 판별하는 장치</b>다.
 */
@Component
public class RouteFacilityCsvReader {

    private static final Logger log = LoggerFactory.getLogger(RouteFacilityCsvReader.class);

    /** 인코딩 후보. UTF-8을 먼저 시도해야 한다 — BikeRouteCsvReader 주석 참조 */
    private static final List<Charset> CANDIDATE_CHARSETS =
            List.of(StandardCharsets.UTF_8, Charset.forName("MS949"));

    /** 원본 구분값 → schema.sql의 facility_type */
    private static final Map<String, String> FACILITY_TYPE_BY_LABEL = Map.of(
            "화장실", "TOILET",
            "급수대", "WATER",
            "인증센터", "CERT_CENTER",
            "공기주입기", "AIR_PUMP"
    );

    /** 컬럼 위치. 노선좌표 CSV와 경도·위도 순서가 반대다 */
    private static final int COL_LABEL = 0;
    private static final int COL_NAME = 1;
    private static final int COL_LONGITUDE = 2;
    private static final int COL_LATITUDE = 3;
    private static final int COLUMN_COUNT = 4;

    /**
     * 알려진 구분값 최소 매칭 비율.
     * 인코딩을 잘못 고르면 구분값이 전부 깨져 0%가 된다. 원본은 4종뿐이라 100%가 정상이다.
     */
    private static final double MIN_LABEL_MATCH_RATIO = 0.95;

    /** 형식 오류 허용 수. 원본에는 결손이 없다(확인함) */
    private static final int SKIP_ALLOWANCE_FLOOR = 5;
    private static final double MAX_SKIP_RATIO = 0.01;

    private static final char BOM = '\uFEFF';

    private final SeedFileProperties properties;

    public RouteFacilityCsvReader(SeedFileProperties properties) {
        this.properties = properties;
    }

    /**
     * 주변시설 좌표를 읽고 완전 중복을 제거한다.
     *
     * @return 판별 인코딩·행 통계와 함께 시설 목록
     */
    public ParseResult readFacilities() {
        Path path = Path.of(properties.bikeRouteFacilities());
        if (!Files.exists(path)) {
            log.error("주변시설 CSV가 없다: {} (공공데이터포털에서 내려받아 배치한다. docs/shared/DATA_SOURCES.md 참조)",
                    path.toAbsolutePath());
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        Decoded decoded = decode(path);

        // 순서를 유지하면서 완전 중복을 걸러야 하므로 LinkedHashSet에 키를 담는다
        Set<String> seen = new LinkedHashSet<>();
        List<Facility> facilities = new ArrayList<>();
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        int dataRows = 0;
        int skipped = 0;
        int duplicates = 0;
        int unknownLabels = 0;

        List<String> lines = decoded.getLines();
        for (int i = 1; i < lines.size(); i++) {   // 0번은 헤더
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            dataRows++;

            String[] columns = line.split(",");
            if (columns.length != COLUMN_COUNT) {
                skipped++;
                continue;
            }
            String label = columns[COL_LABEL].trim();
            String name = columns[COL_NAME].trim();
            double lng;
            double lat;
            try {
                lng = Double.parseDouble(columns[COL_LONGITUDE].trim());
                lat = Double.parseDouble(columns[COL_LATITUDE].trim());
            } catch (NumberFormatException e) {
                skipped++;
                continue;
            }

            labelCounts.merge(label, 1, Integer::sum);
            String facilityType = FACILITY_TYPE_BY_LABEL.get(label);
            if (facilityType == null) {
                // 인코딩 오판이면 여기로 전부 몰린다. 아래 비율 검사가 잡는다
                unknownLabels++;
                continue;
            }

            // (종류, 이름, 좌표)가 같으면 같은 시설이다. DB의 자연키와 같은 기준으로 맞춘다
            if (!seen.add(facilityType + '|' + name + '|' + lng + '|' + lat)) {
                duplicates++;
                continue;
            }
            facilities.add(new Facility(facilityType, name, lng, lat));
        }

        verifyRows(path, dataRows, skipped, decoded.getCharset());
        verifyLabels(path, dataRows, unknownLabels, labelCounts, decoded.getCharset());

        log.info("주변시설 CSV 읽기 완료: 인코딩 {}, 데이터 {}행 → {}건 "
                        + "(중복 {}건 제거, 미상 구분 {}건, 형식 오류 {}행), 종류별 {}",
                decoded.getCharset().name(), dataRows, facilities.size(),
                duplicates, unknownLabels, skipped, labelCounts);

        return new ParseResult(decoded.getCharset().name(), dataRows,
                skipped, duplicates, unknownLabels, facilities);
    }

    private Decoded decode(Path path) {
        for (Charset charset : CANDIDATE_CHARSETS) {
            try {
                List<String> lines = Files.readAllLines(path, charset);
                if (!lines.isEmpty()) {
                    String first = lines.get(0);
                    if (!first.isEmpty() && first.charAt(0) == BOM) {
                        lines.set(0, first.substring(1));
                    }
                }
                return new Decoded(charset, lines);
            } catch (CharacterCodingException e) {
                log.debug("{} 로는 디코딩 실패, 다음 후보 시도", charset.name());
            } catch (IOException e) {
                log.error("주변시설 CSV 읽기 실패: {}", path.toAbsolutePath(), e);
                throw new BusinessException(ErrorCode.COMMON_500);
            }
        }
        log.error("주변시설 CSV 인코딩을 판별하지 못했다: {} (시도: {})",
                path.toAbsolutePath(), CANDIDATE_CHARSETS);
        throw new BusinessException(ErrorCode.COMMON_500);
    }

    private void verifyRows(Path path, int dataRows, int skipped, Charset charset) {
        if (dataRows == 0) {
            log.error("주변시설 CSV에 데이터 행이 없다: {}", path.toAbsolutePath());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        int allowed = Math.max(SKIP_ALLOWANCE_FLOOR, (int) (dataRows * MAX_SKIP_RATIO));
        if (skipped > allowed) {
            log.error("주변시설 CSV 스킵이 과다하다: {}/{}행 (허용 {}행), 판별 인코딩 {}",
                    skipped, dataRows, allowed, charset.name());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /**
     * 구분값이 알려진 4종과 맞는지 본다.
     *
     * 인코딩을 잘못 고르면 파싱은 통과하고 깨진 문자열만 남는다.
     * 좌표는 ASCII라 멀쩡해 보이므로 이 검사가 없으면 조용히 오염된 채로 적재된다.
     */
    private void verifyLabels(Path path, int dataRows, int unknownLabels,
                              Map<String, Integer> labelCounts, Charset charset) {
        double matchRatio = (double) (dataRows - unknownLabels) / dataRows;
        if (matchRatio < MIN_LABEL_MATCH_RATIO) {
            log.error("주변시설 CSV 구분값이 알려진 값과 맞지 않는다: 매칭 {}% (기준 {}%), "
                            + "판별 인코딩 {}, 관측된 구분값 {}. 인코딩 판별이 틀렸을 수 있다: {}",
                    Math.round(matchRatio * 1000) / 10.0, MIN_LABEL_MATCH_RATIO * 100,
                    charset.name(), labelCounts.keySet(), path.toAbsolutePath());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class Decoded {
        private final Charset charset;
        private final List<String> lines;
    }

    /** CSV 파싱 결과 */
    @Getter
    @AllArgsConstructor
    public static class ParseResult {
        private final String charsetName;
        private final int dataRowCount;
        private final int skippedRowCount;
        private final int duplicateRowCount;
        private final int unknownLabelCount;
        private final List<Facility> facilities;
    }

    /** 시설 한 건 */
    @Getter
    @AllArgsConstructor
    public static class Facility {
        /** schema.sql의 facility_type (TOILET·WATER·CERT_CENTER·AIR_PUMP) */
        private final String facilityType;
        private final String name;
        private final double lng;
        private final double lat;
    }
}
