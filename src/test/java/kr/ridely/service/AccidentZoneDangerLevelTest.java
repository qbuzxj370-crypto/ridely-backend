package kr.ridely.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사고다발지 위험 등급 파생 단위 테스트.
 *
 * DB·API 없이 순수 로직만 본다. 등급 규칙은 schema 주석의 정의를 그대로 옮긴 것이라
 * 여기서 고정해두지 않으면 나중에 조용히 바뀔 수 있다.
 *
 * private 메서드를 리플렉션으로 부르는 이유: 등급 파생은 서비스 내부 규칙이고
 * 이것만 쓰려고 public API를 늘리고 싶지 않다. 이 테스트가 사실상 그 규칙의 명세다.
 */
class AccidentZoneDangerLevelTest {

    private final AccidentZoneIngestServiceImpl service =
            new AccidentZoneIngestServiceImpl(null, null, null, null);

    private String level(int occurrence, int death) {
        return (String) ReflectionTestUtils.invokeMethod(
                service, "dangerLevelOf", occurrence, death);
    }

    @ParameterizedTest
    @DisplayName("사고건수로 등급을 나눈다 — 4~5 주의 / 6~9 경고 / 10+ 위험")
    @CsvSource({
            "4,  CAUTION",
            "5,  CAUTION",
            "6,  WARNING",
            "9,  WARNING",
            "10, DANGER",
            "25, DANGER"
    })
    void classifiesByOccurrenceCount(int occurrence, String expected) {
        assertThat(level(occurrence, 0)).isEqualTo(expected);
    }

    @Test
    @DisplayName("사망사고가 있으면 건수와 무관하게 DANGER다")
    void deathOverridesOccurrenceCount() {
        // 선정 기준이 "4건 이상, 단 사망사고 포함 시 3건 이상"이다.
        // 사망이 있으면 건수가 적어도 위험도가 높으므로 건수 판정보다 앞에 둔다.
        assertThat(level(3, 1)).isEqualTo("DANGER");
        assertThat(level(4, 1)).isEqualTo("DANGER");
        assertThat(level(9, 2)).isEqualTo("DANGER");
    }

    @Test
    @DisplayName("선정 기준 아래 건수는 CAUTION으로 떨어뜨린다")
    void belowThresholdFallsBackToCaution() {
        // 사망 없이 3건 이하는 선정 기준상 나올 수 없지만,
        // 원본이 바뀌어도 등급이 비지 않도록 기본값을 둔다(NOT NULL 컬럼이다).
        assertThat(level(3, 0)).isEqualTo("CAUTION");
        assertThat(level(0, 0)).isEqualTo("CAUTION");
    }

    @Test
    @DisplayName("법정동코드 앞 2자리로 시도를 잡는다 — 시도시군구명은 표기가 흔들린다")
    void extractsSidoCodeFromBjdCode() {
        // 2023 "서울 강남구1" vs 2022 "서울특별시 강남구1" — 이름으로는 매핑할 수 없다
        assertThat(sidoCode("1168011800")).isEqualTo("11");
        assertThat(sidoCode("4113510300")).isEqualTo("41");
        assertThat(sidoCode("1")).isNull();
        assertThat(sidoCode(null)).isNull();
    }

    private String sidoCode(String bjdCd) {
        return (String) ReflectionTestUtils.invokeMethod(service, "sidoCodeOf", bjdCd);
    }
}
