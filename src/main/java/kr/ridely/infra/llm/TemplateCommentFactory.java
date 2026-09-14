package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 없이 코치 코멘트를 만든다.
 *
 * 두 자리에서 쓴다. 코멘트 호출이 실패했을 때({@link LlmCallException})와 톤 검증에 걸렸을 때({@link ToneValidator})다. 뒤쪽은 재호출을 거치지 않는다 - 톤 위반이 실측 46건 중 0건이라 LLM을 한 번 더 부를 값이 없다.
 *
 * <b>측정한 사실만 쓴다.</b> 거리·시간·강도·사고다발지는 이미 계산된 값이다. 지어내지 않으므로 틀릴 일이 없다. 반대로 「한강 노을이 아름다운 코스」 같은 문장은 쓰지 않는다 - 그건 LLM이 후보 정보를 보고 하던 일이고, 규칙으로 흉내 내면 근거 없는 장식이 된다.
 *
 * <b>사용자에게 「AI가 실패했다」고 말하지 않는다.</b> 그건 우리 사정이고 코스는 정상이다. 대신 응답의 {@code aiProvider}가 FALLBACK으로 나가 클라이언트가 판단할 수 있게 한다.
 *
 * ⚠️ 위험 구역 안내는 <b>빠뜨리면 안 된다.</b> 코멘트가 대체됐다고 안전 정보까지 사라지면 회피를 끈 사용자가 경고 없이 그 구간을 지난다. 코멘트 품질은 타협해도 이건 아니다.
 */
@Component
public class TemplateCommentFactory {

    /**
     * 어조는 해요체다. 프롬프트가 요구하는 것과 같다 - 「~해봐요」, 「~어때요?」.
     *
     * 처음에는 평서체(「코스다」)로 썼는데 대체됐을 때만 말투가 바뀐다. 톤 검증기는 문구 셋만 보므로 이런 어긋남을 잡지 못한다. 사용자에게는 문장 품질보다 말투가 갑자기 달라지는 쪽이 먼저 눈에 띈다.
     */
    private static final String TITLE = "%.1fkm 라이딩 코스";
    private static final String COMMENT =
            "총 %.1fkm, 예상 %d분 코스예요. 운동 강도는 %s 수준이에요.";
    private static final String COMMENT_NO_DURATION = "총 %.1fkm 코스예요. 운동 강도는 %s 수준이에요.";
    private static final String NEXT_STEP = "라이딩을 시작하면 경로를 따라 안내할게요.";

    private static final String HIGHLIGHT_WAYPOINT = "경유지 %d곳";
    private static final String HIGHLIGHT_DISTANCE = "총 %.1fkm";
    private static final String HIGHLIGHT_INTENSITY = "강도 %s";

    private static final String DANGER_PREFIX = "사고다발지 %d곳을 지나요. ";
    private static final String DANGER_ITEM = "%s(%s)";
    private static final String DANGER_SUFFIX = " 이 구간에서는 속도를 줄이고 주변을 살펴봐요.";

    private static final String LEVEL_DANGER = "DANGER";
    private static final String LEVEL_WARNING = "WARNING";

    /** 사용자에게 보일 등급 이름. CoachCommentClient의 표기와 맞춘다 */
    private static String levelLabel(String level) {
        if (LEVEL_DANGER.equals(level)) {
            return "위험";
        }
        if (LEVEL_WARNING.equals(level)) {
            return "경고";
        }
        return "주의";
    }

    /**
     * 측정값만으로 코멘트를 만든다.
     *
     * @param distanceKm     실측 거리
     * @param durationMin    예상 소요. null이면 문장에서 뺀다
     * @param intensityLevel 강도 라벨
     * @param waypointCount  경유지 수
     * @param dangerZones    지나는 사고다발지. 비어 있으면 안내를 만들지 않는다
     */
    public CoachCommentDTO create(double distanceKm, Integer durationMin, String intensityLevel,
                                  int waypointCount, List<PassingDangerZoneDTO> dangerZones) {

        CoachCommentDTO comment = new CoachCommentDTO();
        comment.setTitle(String.format(TITLE, distanceKm));
        comment.setCoachComment(durationMin == null
                ? String.format(COMMENT_NO_DURATION, distanceKm, intensityLevel)
                : String.format(COMMENT, distanceKm, durationMin, intensityLevel));
        comment.setHighlights(highlights(distanceKm, intensityLevel, waypointCount));
        comment.setDangerZoneAlert(dangerAlert(dangerZones));
        comment.setNextStepSuggestion(NEXT_STEP);
        return comment;
    }

    private List<String> highlights(double distanceKm, String intensityLevel, int waypointCount) {
        List<String> highlights = new ArrayList<>();
        highlights.add(String.format(HIGHLIGHT_DISTANCE, distanceKm));
        if (waypointCount > 0) {
            highlights.add(String.format(HIGHLIGHT_WAYPOINT, waypointCount));
        }
        highlights.add(String.format(HIGHLIGHT_INTENSITY, intensityLevel));
        return highlights;
    }

    /**
     * 지나는 구역을 이름과 등급으로 나열한다.
     *
     * 회피 대상이 아닌 주의 등급도 넣는다. 회피를 켜도 주의는 남으므로, 여기서 빼면 사용자가 실제로 지나는 구역을 모르게 된다.
     */
    private String dangerAlert(List<PassingDangerZoneDTO> zones) {
        if (zones == null || zones.isEmpty()) {
            return null;
        }
        StringBuilder alert = new StringBuilder(String.format(DANGER_PREFIX, zones.size()));
        for (int i = 0; i < zones.size(); i++) {
            PassingDangerZoneDTO zone = zones.get(i);
            if (i > 0) {
                alert.append(", ");
            }
            alert.append(String.format(DANGER_ITEM,
                    zone.getSpotName(), levelLabel(zone.getDangerLevel())));
        }
        return alert.append(DANGER_SUFFIX).toString();
    }
}
