package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 위험 안내가 지나는 구역을 빠뜨렸는지 본다.
 *
 * <b>왜 필요한가.</b> {@code aiDangerZoneAlert}는 LLM이 자유 문장으로 쓴다. 프롬프트가 「지점명과 Nkm 지점을 함께 적어」라고 지시하지만 temperature가 0.6이라 지킨다는 보장이 없다. 세 곳 중 두 곳만 쓰거나 이름을 바꿔 쓸 수 있고, 그러면 라이더가 한 곳을 모르고 지난다.
 *
 * <b>톤 검증과 성격이 다르다.</b> 톤이 어긋나면 어색할 뿐이지만 이쪽은 사고로 이어진다. 그런데 지금까지 둘이 같은 호출·같은 검증에 묶여 있었고, 실제 검증은 문구 셋을 보는 것뿐이었다.
 *
 * <h3>위험·경고만 본다</h3>
 *
 * 주의(CAUTION) 등급은 빼는데, 최신 연도 111건 중 96건이 주의라 전부 나열하면 문장이 길어진다. 프롬프트가 「과장하면 즐거워야 할 라이딩이 불안해진다」고 정한 것과도 부딪힌다. 회피 대상 등급({@code avoid-danger-levels})과 같은 기준이다.
 *
 * <h3>이름 포함만 본다</h3>
 *
 * 거리·등급까지 대조하지 않는 이유는 LLM이 표현을 바꾸기 때문이다. 「3.2km 지점」을 「3km쯤」으로 쓰는 것은 틀린 것이 아니다. 지점명은 고유명사라 그대로 나와야 하고, 그것만으로도 「언급했는가」는 가릴 수 있다.
 *
 * <b>이 검사가 통과해도 문장이 정확하다는 뜻은 아니다.</b> 등급을 낮춰 쓰거나 거리를 틀릴 여지는 남는다. 근본 방어는 응답의 {@code passingDangerZones} 배열이다 - 지점명·등급·건수·좌표가 원본 그대로 실려 나가므로, 화면이 그 배열을 목록으로 그리고 이 문장은 곁들이는 말로 쓰면 LLM 오류가 사용자에게 닿지 않는다. api_spec에 못 박아야 할 계약이다.
 */
@Component
public class DangerAlertValidator {

    private static final Logger log = LoggerFactory.getLogger(DangerAlertValidator.class);

    private static final String LEVEL_DANGER = "DANGER";
    private static final String LEVEL_WARNING = "WARNING";

    /**
     * 위험·경고 구역 중 안내에서 빠진 지점명을 찾는다.
     *
     * @param zones 코스가 지나는 전체 구역. 등급 필터는 여기서 건다
     * @return 빠진 지점명. 하나도 안 빠졌거나 검사 대상이 없으면 null
     */
    public String findOmittedZone(CoachCommentDTO comment, List<PassingDangerZoneDTO> zones) {
        if (zones == null || zones.isEmpty()) {
            return null;
        }
        List<String> mustMention = zones.stream()
                .filter(z -> LEVEL_DANGER.equals(z.getDangerLevel())
                        || LEVEL_WARNING.equals(z.getDangerLevel()))
                .map(PassingDangerZoneDTO::getSpotName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        if (mustMention.isEmpty()) {
            return null;
        }

        // 지나는 위험·경고 구역이 있는데 안내 자체가 없다. 프롬프트는 목록이 비었을 때만
        // 비워 두라고 했으므로 이건 빠뜨린 것이다
        String alert = comment == null ? null : comment.getDangerZoneAlert();
        if (alert == null || alert.isBlank()) {
            log.warn("위험·경고 구역 {}곳을 지나는데 안내가 비어 있다", mustMention.size());
            return mustMention.get(0);
        }

        for (String name : mustMention) {
            if (!alert.contains(name)) {
                log.warn("위험 안내가 구역을 빠뜨렸다: {}", name);
                return name;
            }
        }
        return null;
    }
}
