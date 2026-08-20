package kr.ridely.infra.llm;

import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사고다발지 프롬프트 행 조립 단위 테스트.
 *
 * LLM·DB 없이 순수 변환만 본다. private 메서드를 리플렉션으로 부르는 방식은 AccidentZoneDangerLevelTest의 선례를 따랐다.
 *
 * 여기서 고정하려는 것은 <b>사망자가 있으면 반드시 문장에 드러난다</b>는 규약이다. 등급이 DANGER로 올라가는 조건이 "사고 10건 이상" 아니면 "사망사고 포함"인데, 후자는 건수가 적어 문장에서 빠지면 라이더가 위험도를 오해한다. 실측에서 걸리는 구역이 대부분 사고 4~5건이라 그 차이가 더 중요하다.
 */
class CoachCommentDangerRowTest {

    private final CoachCommentClient client = new CoachCommentClient(null, null, null, null);

    @Test
    @DisplayName("사망자가 없으면 사고 건수만 적는다")
    void writesOccurrenceOnly() {
        assertThat(detailOf(zone(5, 0))).isEqualTo("최근 1년 사고 5건");
    }

    @Test
    @DisplayName("사망자가 있으면 반드시 함께 적는다")
    void alwaysRevealsDeaths() {
        // 사고 3건이어도 사망이 있으면 DANGER다. 건수만 보이면 등급과 문장이 어긋난다
        assertThat(detailOf(zone(3, 1))).isEqualTo("최근 1년 사고 3건, 사망 1명");
        assertThat(detailOf(zone(12, 2))).isEqualTo("최근 1년 사고 12건, 사망 2명");
    }

    @Test
    @DisplayName("건수가 null이어도 문장이 깨지지 않는다")
    void toleratesNullCounts() {
        // 스키마상 NOT NULL DEFAULT 0이라 실제로는 오지 않지만,
        // 여기서 NPE가 나면 코멘트 생성 전체가 실패한다
        PassingDangerZoneDTO zone = new PassingDangerZoneDTO();
        assertThat(detailOf(zone)).isEqualTo("최근 1년 사고 0건");
    }

    @Test
    @DisplayName("등급을 한국어로 바꾼다 — 프롬프트의 톤 지시와 같은 말이어야 한다")
    void translatesDangerLevel() {
        assertThat(levelOf("CAUTION")).isEqualTo("주의");
        assertThat(levelOf("WARNING")).isEqualTo("경고");
        assertThat(levelOf("DANGER")).isEqualTo("위험");
    }

    @Test
    @DisplayName("모르는 등급은 원본을 그대로 둔다")
    void keepsUnknownLevelAsIs() {
        // 스키마 CHECK가 세 값만 허용하므로 실제로는 오지 않는다.
        // 다만 조용히 빈 값이 되어 프롬프트에서 등급이 사라지는 것보다는 낫다
        assertThat(levelOf("SEVERE")).isEqualTo("SEVERE");
    }

    @Test
    @DisplayName("진행 거리가 없으면 물음표로 둔다")
    void marksUnknownProgress() {
        PassingDangerZoneDTO zone = zone(4, 0);
        zone.setDistanceFromStartKm(null);

        assertThat(rowsOf(List.of(zone)).getFirst()).containsEntry("progressKm", "?");
    }

    @Test
    @DisplayName("목록이 비어 있거나 null이면 빈 목록이다 — 템플릿이 경고 섹션을 통째로 건너뛴다")
    void emptyWhenNoZones() {
        assertThat(rowsOf(List.of())).isEmpty();
        assertThat(rowsOf(null)).isEmpty();
    }

    @Test
    @DisplayName("한 구역이 네 값을 모두 채운다")
    void fillsEveryColumn() {
        PassingDangerZoneDTO zone = zone(8, 0);
        zone.setDangerLevel("WARNING");
        zone.setSpotName("서울 동작구 본동(한강대교남단교차로 부근)");
        zone.setDistanceFromStartKm(new BigDecimal("3.2"));

        assertThat(rowsOf(List.of(zone)).getFirst())
                .containsEntry("progressKm", "3.2")
                .containsEntry("level", "경고")
                .containsEntry("name", "서울 동작구 본동(한강대교남단교차로 부근)")
                .containsEntry("detail", "최근 1년 사고 8건");
    }

    // --- 도우미 ---

    private PassingDangerZoneDTO zone(int occurrences, int deaths) {
        PassingDangerZoneDTO zone = new PassingDangerZoneDTO();
        zone.setDangerLevel("CAUTION");
        zone.setSpotName("테스트 지점");
        zone.setOccurrenceCount(occurrences);
        zone.setDeathCount(deaths);
        zone.setDistanceFromStartKm(new BigDecimal("1.0"));
        return zone;
    }

    private String detailOf(PassingDangerZoneDTO zone) {
        return (String) ReflectionTestUtils.invokeMethod(client, "dangerDetail", zone);
    }

    private String levelOf(String dangerLevel) {
        PassingDangerZoneDTO zone = zone(4, 0);
        zone.setDangerLevel(dangerLevel);
        return rowsOf(List.of(zone)).getFirst().get("level");
    }

    /**
     * 인자를 배열로 감싸는 이유는 가변인자 함정 때문이다.
     *
     * invokeMethod(target, name, null)로 부르면 자바가 null을 "인자 배열 자체가 null"로 읽어 인자 없는 메서드를 찾는다. 배열로 감싸야 "null 인자 하나"가 된다.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> rowsOf(List<PassingDangerZoneDTO> zones) {
        return (List<Map<String, String>>) ReflectionTestUtils.invokeMethod(
                client, "toDangerRows", new Object[]{zones});
    }
}
