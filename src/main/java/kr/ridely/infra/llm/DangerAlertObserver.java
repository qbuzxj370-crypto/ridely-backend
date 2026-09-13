package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 위험 안내가 비었는지 <b>기록만</b> 한다. 응답은 바꾸지 않는다.
 *
 * <h3>왜 검사가 아니라 관측인가</h3>
 *
 * 처음에는 「위험·경고 구역의 지점명이 안내 문장에 들어 있는가」를 검사하고, 빠졌으면 코멘트를 템플릿으로 바꿨다. 그 설계가 틀렸다.
 *
 * <b>첫째, 통과할 수 없는 조건이었다.</b> {@code spot_name}은 도로교통공단 표기라 「서울 동작구 본동(한강대교남단교차로 부근)」 형태인데, 프롬프트가 코치 톤을 요구하므로 LLM은 「한강대교 남단」으로 줄여 쓴다. 2026-09-13 운영에서 걸린 1건이 정확히 그 경우였고, 테스트 데이터를 실제 형식으로 바꾸자 8건 중 4건이 깨졌다. 오탐 1건, 정탐 0건이다.
 *
 * <b>둘째, 층을 잘못 잡았다.</b> 라이더에게 위치를 알리는 것은 이 문장의 일이 아니다.
 *
 * <pre>
 * 코스 확인   passingDangerZones의 polygonGeoJson·좌표를 지도에 표시   결정론적
 * 라이딩 중   GPS 근접 알림 (ridely.route.danger-zone-alert-distance-m)  결정론적
 * 코치 코멘트  톤과 맥락                                                LLM
 * </pre>
 *
 * 위 둘이 위치를 정확히 전달한다. 문장이 한 곳을 덜 언급해도 라이더는 지도와 알림으로 안다. <b>근거는 {@code passingDangerZones} 배열이고 문장은 그 위에 얹는 해설인데, 검사는 그 문장을 근거처럼 다루고 있었다.</b>
 *
 * <h3>그래서 무엇을 남겼나</h3>
 *
 * 지나는 구역이 있는데 안내가 통째로 빈 경우만 센다. 이건 안전이 아니라 응답 완성도 문제이고, <b>실제로 얼마나 자주 나는지 모른다</b> - 운영 관측이 0건이다. 빈도를 모르는 채로 대체를 넣으면 겪지 않은 문제에 대가를 치른다.
 *
 * 응답을 바꾸지 않는 이유가 하나 더 있다. 이 필드만 규칙으로 채우면 {@code aiProvider}가 어정쩡해진다 - 코치 코멘트는 LLM 것인데 FALLBACK으로 표시하면 과잉이고, gemini로 두면 규칙이 만든 문장에 AI 딱지가 붙는다.
 *
 * <b>이 로그가 쌓이면 결정이 데이터로 내려진다.</b> 자주 나면 그때 프롬프트를 고치거나 필드를 채우고, 안 나면 이 클래스도 지운다.
 */
@Component
public class DangerAlertObserver {

    private static final Logger log = LoggerFactory.getLogger(DangerAlertObserver.class);

    private static final String LEVEL_DANGER = "DANGER";
    private static final String LEVEL_WARNING = "WARNING";

    /**
     * 위험·경고를 지나는데 안내가 비었으면 로그를 남긴다.
     *
     * 주의(CAUTION)만 지나는 경우는 세지 않는다. 프롬프트가 「과장하면 즐거워야 할 라이딩이 불안해진다」고 정했고 111건 중 96건이 주의라, 그때 안내가 없는 것은 정상 범위다.
     */
    public void record(CoachCommentDTO comment, List<PassingDangerZoneDTO> zones) {
        if (zones == null || zones.isEmpty()) {
            return;
        }
        long seriousCount = zones.stream()
                .filter(z -> LEVEL_DANGER.equals(z.getDangerLevel())
                        || LEVEL_WARNING.equals(z.getDangerLevel()))
                .count();
        if (seriousCount == 0) {
            return;
        }

        String alert = comment == null ? null : comment.getDangerZoneAlert();
        if (alert == null || alert.isBlank()) {
            log.warn("위험·경고 구역 {}곳을 지나는데 안내가 비어 있다. 응답은 그대로 내보낸다", seriousCount);
        }
    }
}
