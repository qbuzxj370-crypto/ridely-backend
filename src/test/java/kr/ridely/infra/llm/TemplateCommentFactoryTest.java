package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙으로 만드는 코치 코멘트 단위 테스트.
 *
 * <b>가장 중요한 것은 위험 구역 안내가 빠지지 않는 것이다.</b> 코멘트가 대체됐다고 안전 정보까지 사라지면 회피를 끈 사용자가 경고 없이 그 구간을 지난다. 코멘트 품질은 타협해도 이건 아니다.
 *
 * 나머지는 측정값을 그대로 옮기는지만 본다. 지어내지 않으므로 틀릴 일이 없고, 반대로 문장을 예쁘게 만들려다 없는 사실이 들어가는 것을 막는다.
 */
class TemplateCommentFactoryTest {

    private static final double DISTANCE_KM = 12.4;
    private static final int DURATION_MIN = 46;
    private static final String INTENSITY = "MODERATE";
    private static final int WAYPOINT_COUNT = 3;

    private final TemplateCommentFactory factory = new TemplateCommentFactory();

    @Test
    @DisplayName("회피 대상이 아닌 주의 등급도 안내에 넣는다")
    void includesCautionZones() {
        // 회피를 켜도 주의는 남는다. 여기서 빼면 사용자가 실제로 지나는 구역을 모른다
        CoachCommentDTO comment = factory.create(DISTANCE_KM, DURATION_MIN, INTENSITY,
                WAYPOINT_COUNT, List.of(zone("여의나루역 앞", "CAUTION")));

        assertThat(comment.getDangerZoneAlert())
                .contains("여의나루역 앞")
                .contains("주의");
    }

    @Test
    @DisplayName("등급을 우리말로 바꿔 쓴다")
    void translatesDangerLevels() {
        CoachCommentDTO comment = factory.create(DISTANCE_KM, DURATION_MIN, INTENSITY,
                WAYPOINT_COUNT, List.of(
                        zone("한강대교 남단", "WARNING"),
                        zone("성내동 사거리", "DANGER")));

        assertThat(comment.getDangerZoneAlert())
                .contains("경고")
                .contains("위험")
                .doesNotContain("WARNING")
                .doesNotContain("DANGER");
    }

    @Test
    @DisplayName("지나는 구역이 없으면 안내를 만들지 않는다")
    void noAlertWithoutZones() {
        // 빈 문자열이 아니라 null이어야 화면이 영역을 통째로 감춘다
        assertThat(factory.create(DISTANCE_KM, DURATION_MIN, INTENSITY, WAYPOINT_COUNT, List.of())
                .getDangerZoneAlert()).isNull();
        assertThat(factory.create(DISTANCE_KM, DURATION_MIN, INTENSITY, WAYPOINT_COUNT, null)
                .getDangerZoneAlert()).isNull();
    }

    @Test
    @DisplayName("측정값을 그대로 옮긴다")
    void carriesMeasuredValues() {
        CoachCommentDTO comment = factory.create(DISTANCE_KM, DURATION_MIN, INTENSITY,
                WAYPOINT_COUNT, List.of());

        assertThat(comment.getTitle()).contains("12.4");
        assertThat(comment.getCoachComment()).contains("12.4").contains("46").contains(INTENSITY);
        assertThat(comment.getHighlights()).anyMatch(h -> h.contains("3"));
    }

    @Test
    @DisplayName("소요 시간을 모르면 문장에서 뺀다")
    void omitsUnknownDuration() {
        // 모르는 값을 0분으로 쓰면 "예상 0분"이 나간다
        CoachCommentDTO comment = factory.create(DISTANCE_KM, null, INTENSITY,
                WAYPOINT_COUNT, List.of());

        assertThat(comment.getCoachComment()).contains("12.4").doesNotContain("분");
    }

    @Test
    @DisplayName("경유지가 없으면 하이라이트에서 뺀다")
    void omitsEmptyWaypointCount() {
        CoachCommentDTO comment = factory.create(DISTANCE_KM, DURATION_MIN, INTENSITY,
                0, List.of());

        assertThat(comment.getHighlights()).noneMatch(h -> h.contains("경유지"));
    }

    private PassingDangerZoneDTO zone(String spotName, String dangerLevel) {
        PassingDangerZoneDTO zone = new PassingDangerZoneDTO();
        zone.setSpotName(spotName);
        zone.setDangerLevel(dangerLevel);
        zone.setOccurrenceCount(6);
        return zone;
    }
}
