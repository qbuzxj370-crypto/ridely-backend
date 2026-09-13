package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 위험 안내 누락 검사 단위 테스트.
 *
 * <b>여기서 고정하려는 것은 「위험·경고는 반드시 언급된다」와 「주의까지 강요하지는 않는다」 둘이다.</b> 앞을 놓치면 라이더가 위험 구역을 모르고 지나고, 뒤를 강요하면 최신 연도 111건 중 96건인 주의 등급이 전부 문장에 들어와 프롬프트가 막으려던 「과장」이 된다.
 */
class DangerAlertValidatorTest {

    private static final String ZONE_A = "한강대교 남단";
    private static final String ZONE_B = "성내동 사거리";
    private static final String ZONE_CAUTION = "여의나루역 앞";

    private final DangerAlertValidator validator = new DangerAlertValidator();

    @Test
    @DisplayName("위험·경고를 전부 언급하면 통과한다")
    void passesWhenAllSeriousZonesMentioned() {
        assertThat(validator.findOmittedZone(
                alert(ZONE_A + " 3km 지점과 " + ZONE_B + " 8km 지점에서 페이스를 늦춰요"),
                List.of(zone(ZONE_A, "WARNING"), zone(ZONE_B, "DANGER"))))
                .isNull();
    }

    @Test
    @DisplayName("한 곳이라도 빠지면 그 지점명을 돌려준다")
    void detectsOmittedZone() {
        // 세 곳 중 두 곳만 쓰는 것이 실제로 우려되는 형태다
        assertThat(validator.findOmittedZone(
                alert(ZONE_A + " 부근에서 속도를 줄여요"),
                List.of(zone(ZONE_A, "WARNING"), zone(ZONE_B, "DANGER"))))
                .isEqualTo(ZONE_B);
    }

    @Test
    @DisplayName("위험·경고를 지나는데 안내가 비어 있으면 누락이다")
    void treatsEmptyAlertAsOmission() {
        // 프롬프트는 목록이 비었을 때만 비워 두라고 했다
        assertThat(validator.findOmittedZone(alert(null), List.of(zone(ZONE_A, "DANGER"))))
                .isEqualTo(ZONE_A);
        assertThat(validator.findOmittedZone(alert("   "), List.of(zone(ZONE_A, "DANGER"))))
                .isEqualTo(ZONE_A);
    }

    @Test
    @DisplayName("주의 등급은 언급하지 않아도 통과한다")
    void doesNotRequireCautionZones() {
        // 111건 중 96건이 주의다. 전부 나열을 강요하면 문장이 길어지고
        // 프롬프트가 막으려던 과장이 된다
        assertThat(validator.findOmittedZone(
                alert(ZONE_A + "에서 페이스를 늦춰요"),
                List.of(zone(ZONE_A, "WARNING"), zone(ZONE_CAUTION, "CAUTION"))))
                .isNull();
    }

    @Test
    @DisplayName("주의 등급만 지나면 안내가 없어도 통과한다")
    void passesWhenOnlyCautionZones() {
        assertThat(validator.findOmittedZone(alert(null), List.of(zone(ZONE_CAUTION, "CAUTION"))))
                .isNull();
    }

    @Test
    @DisplayName("지나는 구역이 없으면 검사하지 않는다")
    void skipsWithoutZones() {
        assertThat(validator.findOmittedZone(alert(null), List.of())).isNull();
        assertThat(validator.findOmittedZone(alert(null), null)).isNull();
    }

    @Test
    @DisplayName("거리 표현이 달라도 지점명만 있으면 통과한다")
    void toleratesDifferentDistanceWording() {
        // "3.2km 지점"을 "3km쯤"으로 쓰는 것은 틀린 것이 아니다.
        // 고유명사인 지점명만 대조한다
        assertThat(validator.findOmittedZone(
                alert("3km쯤 " + ZONE_A + " 근처는 살피면서 지나가요"),
                List.of(zone(ZONE_A, "WARNING"))))
                .isNull();
    }

    private CoachCommentDTO alert(String dangerZoneAlert) {
        CoachCommentDTO comment = new CoachCommentDTO();
        comment.setCoachComment("한강 따라 달려봐요");
        comment.setDangerZoneAlert(dangerZoneAlert);
        return comment;
    }

    private PassingDangerZoneDTO zone(String spotName, String dangerLevel) {
        PassingDangerZoneDTO zone = new PassingDangerZoneDTO();
        zone.setSpotName(spotName);
        zone.setDangerLevel(dangerLevel);
        zone.setOccurrenceCount(7);
        return zone;
    }
}
