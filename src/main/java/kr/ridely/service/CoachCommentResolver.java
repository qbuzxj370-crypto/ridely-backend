package kr.ridely.service;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.infra.llm.CoachCommentClient;
import kr.ridely.infra.llm.DangerAlertObserver;
import kr.ridely.infra.llm.LlmCallException;
import kr.ridely.infra.llm.TemplateCommentFactory;
import kr.ridely.infra.llm.ToneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 코치 코멘트를 확정한다. LLM이 안 되거나 톤을 벗어나면 템플릿으로 바꾼다.
 *
 * 설계 쪽({@link CourseDesignResolver})과 나눈 이유는 실패를 다루는 방식이 달라서다. 설계는 재호출이 있고 코멘트는 없다.
 *
 * <b>응답을 바꾸는 검사는 톤 하나뿐이다.</b> 위험 안내 쪽({@link DangerAlertObserver})은 세기만 한다 - 라이더에게 위치를 알리는 것은 지도와 GPS 근접 알림의 일이고, 그 둘은 LLM을 거치지 않는다. 한때 안내 문장을 검사해 대체했는데 설계가 틀렸다. 이유는 DangerAlertObserver 주석에 있다.
 *
 * <b>톤 위반에 재호출을 두지 않았다.</b> 설계 문서는 「commentate 재호출 → 템플릿」이었으나 중간 단계를 뺐다. DB에 쌓인 코멘트 46건 전부에 판정 문구를 걸어 본 결과 위반이 0건이었다(2026-09-13). 이 빈도에 LLM을 한 번 더 부르면 쿼터와 응답 시간만 쓴다.
 *
 * ⚠️ 0건은 「나지 않는다」가 아니라 「46번 중엔 없었다」다. 검증기를 만들되 재호출을 뺀 것이 그 균형이다.
 *
 * <b>톤 위반도 대체로 센다.</b> LLM이 만든 문장을 버리고 템플릿을 내보내는 것이라 사용자가 받는 것은 호출 실패와 다르지 않다. aiProvider가 FALLBACK으로 나가야 화면이 「AI 코치」로 표시하지 않는다.
 */
@Component
public class CoachCommentResolver {

    private static final Logger log = LoggerFactory.getLogger(CoachCommentResolver.class);

    private final CoachCommentClient coachCommentClient;
    private final ToneValidator toneValidator;
    private final DangerAlertObserver dangerAlertObserver;
    private final TemplateCommentFactory templateCommentFactory;

    public CoachCommentResolver(CoachCommentClient coachCommentClient,
                                ToneValidator toneValidator,
                                DangerAlertObserver dangerAlertObserver,
                                TemplateCommentFactory templateCommentFactory) {
        this.coachCommentClient = coachCommentClient;
        this.toneValidator = toneValidator;
        this.dangerAlertObserver = dangerAlertObserver;
        this.templateCommentFactory = templateCommentFactory;
    }

    /** 코멘트와 대체 여부. 호출 실패뿐 아니라 톤 위반으로 바꿔도 대체다 */
    public record Resolved(CoachCommentDTO comment, boolean fallback) {
    }

    /**
     * @param distanceKm    실측 거리. 목표가 아니라 ORS가 그린 결과다
     * @param waypointCount 템플릿이 쓸 경유지 수. 대체 시 하이라이트에 들어간다
     */
    public Resolved resolve(CourseDesignDTO design, RouteCandidatesDTO candidates,
                            double targetDistanceKm, boolean circular,
                            double distanceKm, Integer durationMin, String intensityLevel,
                            List<PassingDangerZoneDTO> dangerZones,
                            boolean avoidApplied, int waypointCount) {
        try {
            CoachCommentDTO comment = coachCommentClient.generate(
                    design, candidates, targetDistanceKm, circular,
                    distanceKm, durationMin, intensityLevel, dangerZones, avoidApplied);

            // 위험 안내가 비었는지 세기만 한다. 응답은 바꾸지 않는다 - 라이더에게 위치를
            // 알리는 것은 지도와 GPS 근접 알림의 일이지 이 문장의 일이 아니다
            dangerAlertObserver.record(comment, dangerZones);

            String violation = toneValidator.findViolation(comment);
            if (violation == null) {
                return new Resolved(comment, false);
            }
            log.warn("코치 코멘트를 템플릿으로 바꾼다. 톤 위반 문구={} / 버린 코멘트={}",
                    violation, comment.getCoachComment());

        } catch (LlmCallException e) {
            log.warn("코치 코멘트 호출이 실패했다. 템플릿으로 바꾼다: {}", e.getMessage());
        }

        return new Resolved(template(distanceKm, durationMin, intensityLevel,
                waypointCount, dangerZones), true);
    }

    /**
     * 규칙으로 코멘트를 만든다.
     *
     * 안내를 빠뜨려 바꾸는 경우에도 이걸 쓴다. 템플릿은 DB 값을 그대로 조립하므로 <b>빠뜨릴 수가 없다</b> - LLM이 놓친 구역을 채우는 데 이보다 확실한 방법이 없다.
     */
    private CoachCommentDTO template(double distanceKm, Integer durationMin, String intensityLevel,
                                     int waypointCount, List<PassingDangerZoneDTO> dangerZones) {
        return templateCommentFactory.create(
                distanceKm, durationMin, intensityLevel, waypointCount, dangerZones);
    }
}
