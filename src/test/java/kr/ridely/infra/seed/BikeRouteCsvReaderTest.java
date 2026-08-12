package kr.ridely.infra.seed;

import kr.ridely.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 노선 좌표 CSV 파서 단위 테스트.
 *
 * DB나 Spring 컨텍스트가 필요 없는 파싱 로직만 여기서 본다.
 * 적재·영속은 {@code BikeRouteIngestTest}(통합)가 맡는다.
 *
 * <p>인코딩 판별을 여기서 검증하는 이유: 통합 테스트는 파일 경로를
 * {@code @DynamicPropertySource}로 한 번만 주입하므로 한 컨텍스트에서
 * 두 인코딩을 시험할 수 없다.
 */
class BikeRouteCsvReaderTest {

    @TempDir
    Path tempDir;

    /** 파일명은 ASCII로 둔다. 실행 환경의 파일명 인코딩(sun.jnu.encoding)에 영향받지 않게 */
    private BikeRouteCsvReader readerFor(String csv, Charset charset) throws IOException {
        Path file = tempDir.resolve("coords-" + charset.name() + ".csv");
        Files.write(file, csv.getBytes(charset));
        return new BikeRouteCsvReader(new SeedFileProperties(file.toString(), "unused.csv"));
    }

    /**
     * 원본 형식 그대로 <b>3번째 열이 위도, 4번째 열이 경도</b>다.
     * 검증 목적을 행마다 심어 두었다.
     */
    private static final String NORMAL_CSV = """
            순서,국토종주 자전거길,위도(LINE_XP),경도(LINE_YP)
            3,1,37.5900,126.6100
            1,1,37.5573,126.6034
            2,1,37.5740,126.6070
            1,2,37.5434,126.8997
            2,2,37.5265,126.9339
            1,14,37.7700,128.8900
            2,14,37.7800,128.9000
            """;

    @Test
    @DisplayName("UTF-8 파일을 판별해 읽는다")
    void detectsUtf8() throws IOException {
        BikeRouteCsvReader.ParseResult result =
                readerFor(NORMAL_CSV, StandardCharsets.UTF_8).readRouteCoordinates();

        assertThat(result.getCharsetName()).isEqualTo("UTF-8");
        assertThat(result.getCoordinatesByRoute()).containsOnlyKeys(1, 2);
    }

    @Test
    @DisplayName("CP949 파일을 판별해 읽는다 — 원본 배포본 인코딩")
    void detectsCp949() throws IOException {
        BikeRouteCsvReader.ParseResult result =
                readerFor(NORMAL_CSV, Charset.forName("MS949")).readRouteCoordinates();

        // UTF-8 엄격 디코딩이 CP949 한글에서 실패해야 두 번째 후보로 넘어간다.
        // 순서가 반대면 CP949가 UTF-8 파일도 깨진 채로 "성공"시킨다.
        // 정규 이름은 JDK가 정하므로(x-windows-949) 부분 문자열로 본다
        assertThat(result.getCharsetName()).contains("949");
        assertThat(result.getCoordinatesByRoute()).containsOnlyKeys(1, 2);
    }

    @Test
    @DisplayName("순번으로 정렬하고 경도·위도를 뒤집지 않는다")
    void sortsBySequenceAndKeepsAxisOrder() throws IOException {
        Map<Integer, List<BikeRouteCsvReader.Coordinate>> byRoute =
                readerFor(NORMAL_CSV, StandardCharsets.UTF_8).readRouteCoordinates()
                        .getCoordinatesByRoute();

        List<BikeRouteCsvReader.Coordinate> ara = byRoute.get(1);
        // CSV에 순서가 3,1,2로 뒤섞여 있으므로 정렬이 동작해야 첫 점이 순번 1이다
        assertThat(ara.get(0).getLat()).isEqualTo(37.5573);
        assertThat(ara.get(0).getLng()).isEqualTo(126.6034);   // 3열=위도, 4열=경도
        assertThat(ara.get(2).getLat()).isEqualTo(37.5900);
    }

    @Test
    @DisplayName("지역 자전거길(코드 14+)은 결손이 아니라 정상 필터다")
    void filtersNonNationalRoutesWithoutCountingAsSkipped() throws IOException {
        BikeRouteCsvReader.ParseResult result =
                readerFor(NORMAL_CSV, StandardCharsets.UTF_8).readRouteCoordinates();

        // 원본에서 코드 14+가 8% 가까이 된다. 결손과 함께 세면 스킵 비율 검사에 걸린다
        assertThat(result.getDataRowCount()).isEqualTo(7);
        assertThat(result.getSkippedRowCount()).isZero();
        assertThat(result.getCoordinatesByRoute()).doesNotContainKey(14);
    }

    @Test
    @DisplayName("따옴표 안 콤마로 열이 5개가 된 결손 행을 건너뛴다 — 원본 38569·38570")
    void skipsMalformedRowWithQuotedComma() throws IOException {
        String csv = """
                순서,국토종주 자전거길,위도(LINE_XP),경도(LINE_YP)
                1,11,37.80942,128.901723
                2,11,"5, latit",128.902086
                3,11,37.8091542,128.9021164
                """;

        BikeRouteCsvReader.ParseResult result =
                readerFor(csv, StandardCharsets.UTF_8).readRouteCoordinates();

        assertThat(result.getSkippedRowCount()).isEqualTo(1);
        assertThat(result.getCoordinatesByRoute().get(11)).hasSize(2);
    }

    @Test
    @DisplayName("결손이 과다하면 조용히 넘기지 않고 실패한다")
    void failsWhenTooManyRowsSkipped() throws IOException {
        StringBuilder csv = new StringBuilder("순서,국토종주 자전거길,위도(LINE_XP),경도(LINE_YP)\n");
        for (int i = 1; i <= 100; i++) {
            csv.append(i).append(",1,37.5,126.6\n");
        }
        for (int i = 101; i <= 120; i++) {          // 허용치(max(5, 1%))를 넘는 결손
            csv.append(i).append(",1,망가진값,126.6\n");
        }

        BikeRouteCsvReader reader = readerFor(csv.toString(), StandardCharsets.UTF_8);

        assertThatThrownBy(reader::readRouteCoordinates)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("파일이 없으면 실패한다")
    void failsWhenFileMissing() {
        BikeRouteCsvReader reader =
                new BikeRouteCsvReader(new SeedFileProperties("db/seed/없는파일.csv", "unused.csv"));

        assertThatThrownBy(reader::readRouteCoordinates)
                .isInstanceOf(BusinessException.class);
    }
}
