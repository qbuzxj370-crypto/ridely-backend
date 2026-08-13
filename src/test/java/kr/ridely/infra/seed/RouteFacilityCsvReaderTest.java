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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 주변시설 CSV 파서 단위 테스트.
 *
 * DB나 Spring 컨텍스트가 필요 없는 파싱 로직만 여기서 본다.
 * 적재·노선 연결은 {@code RouteFacilityIngestTest}(통합)가 맡는다.
 */
class RouteFacilityCsvReaderTest {

    @TempDir
    Path tempDir;

    private RouteFacilityCsvReader readerFor(String csv, Charset charset) throws IOException {
        Path file = tempDir.resolve("facilities-" + charset.name() + ".csv");
        Files.write(file, csv.getBytes(charset));
        return new RouteFacilityCsvReader(new SeedFileProperties("unused.csv", file.toString()));
    }

    /**
     * 3열이 경도, 4열이 위도다. 노선좌표 CSV와 반대라 여기서 뒤집히면 좌표가 통째로 어긋난다.
     * 마지막 두 행은 앞 행과 완전히 같은 중복이다.
     */
    private static final String NORMAL_CSV = """
            구분,이름,경도,위도
            인증센터,아라서해갑문인증센터,126.5990,37.5820
            화장실,한강종주길,126.8997,37.5434
            급수대,한강종주길,126.9339,37.5265
            공기주입기,오천길,127.4000,36.6000
            화장실,한강종주길,126.8997,37.5434
            급수대,한강종주길,126.9339,37.5265
            """;

    @Test
    @DisplayName("구분값을 facility_type으로 옮기고 경도·위도 순서를 지킨다")
    void mapsLabelAndKeepsAxisOrder() throws IOException {
        List<RouteFacilityCsvReader.Facility> facilities =
                readerFor(NORMAL_CSV, StandardCharsets.UTF_8).readFacilities().getFacilities();

        assertThat(facilities)
                .extracting(RouteFacilityCsvReader.Facility::getFacilityType,
                        RouteFacilityCsvReader.Facility::getLng,
                        RouteFacilityCsvReader.Facility::getLat)
                .containsExactly(
                        tuple("CERT_CENTER", 126.5990, 37.5820),
                        tuple("TOILET", 126.8997, 37.5434),
                        tuple("WATER", 126.9339, 37.5265),
                        tuple("AIR_PUMP", 127.4000, 36.6000));
    }

    @Test
    @DisplayName("완전 중복을 제거한다 — 원본에 109건 있다")
    void removesExactDuplicates() throws IOException {
        RouteFacilityCsvReader.ParseResult result =
                readerFor(NORMAL_CSV, StandardCharsets.UTF_8).readFacilities();

        assertThat(result.getDataRowCount()).isEqualTo(6);
        assertThat(result.getDuplicateRowCount()).isEqualTo(2);
        assertThat(result.getFacilities()).hasSize(4);
    }

    @Test
    @DisplayName("CP949 파일을 판별해 읽는다 — 원본 배포본 인코딩")
    void detectsCp949() throws IOException {
        RouteFacilityCsvReader.ParseResult result =
                readerFor(NORMAL_CSV, Charset.forName("MS949")).readFacilities();

        assertThat(result.getCharsetName()).contains("949");
        assertThat(result.getUnknownLabelCount()).isZero();
        assertThat(result.getFacilities()).hasSize(4);
    }

    @Test
    @DisplayName("UTF-8 파일을 판별해 읽는다")
    void detectsUtf8() throws IOException {
        RouteFacilityCsvReader.ParseResult result =
                readerFor(NORMAL_CSV, StandardCharsets.UTF_8).readFacilities();

        assertThat(result.getCharsetName()).isEqualTo("UTF-8");
        assertThat(result.getUnknownLabelCount()).isZero();
    }

    @Test
    @DisplayName("구분값이 알려진 4종과 안 맞으면 실패한다 — 인코딩 오판 방어선")
    void failsWhenLabelsUnrecognized() throws IOException {
        // 인코딩을 잘못 고르면 구분값이 이렇게 깨진다. 좌표는 ASCII라 멀쩡해서
        // 이 검사가 없으면 파싱이 통과하고 오염된 문자열이 그대로 DB에 들어간다
        String broken = """
                援щ텇,�대쫫,寃쎈룄,�꾨룄
                �붾뜑,�곗씠�,126.5990,37.5820
                �붾뜑,�곗씠�,126.8997,37.5434
                """;

        RouteFacilityCsvReader reader = readerFor(broken, StandardCharsets.UTF_8);

        assertThatThrownBy(reader::readFacilities)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("파일이 없으면 실패한다")
    void failsWhenFileMissing() {
        RouteFacilityCsvReader reader = new RouteFacilityCsvReader(
                new SeedFileProperties("unused.csv", "db/seed/없는파일.csv"));

        assertThatThrownBy(reader::readFacilities)
                .isInstanceOf(BusinessException.class);
    }
}
