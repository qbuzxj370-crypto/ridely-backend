package kr.ridely.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.common.IdempotencyKey;
import kr.ridely.config.RouteProperties;
import kr.ridely.dao.RecommendationCacheDao;
import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 같은 요청이 다시 오면 첫 응답을 그대로 돌려준다.
 *
 * <b>이름은 캐시지만 하는 일은 멱등성이다.</b> 요청 조건으로 키를 만들지 않고 클라이언트가 보낸 {@code Idempotency-Key}를 쓴다. 조건으로 만들면 「재시도인가 새 요청인가」를 우리가 추측해야 하는데, GPS 오차가 ±5~20m라 같은 자리에서도 좌표가 흔들리고 재추천은 조건이 같아 갇힌다. 클라이언트가 명시하면 추측할 일이 없다.
 *
 * <b>노리는 것은 네트워크 재시도다.</b> 추천 응답이 10초라 모바일에서 실재하고, 그 한 번이 LLM을 2~4회 더 쓴다. 「앱을 껐다 켜고 다시 요청」은 새 키가 나가 걸리지 않는데 그게 맞다 - 다시 켜고 요청한 것은 새 코스를 원하는 것이다.
 *
 * 서비스에서 떼어 낸 이유는 테스트다. {@code RouteRecommendServiceImpl}은 생성자 인자가 열다섯이라 이 분기만 보려 해도 전체를 세워야 한다. 여기 의존은 셋이라 익명 클래스로 흉내 낼 수 있다.
 */
@Component
public class RecommendationReplayStore {

    private static final Logger log = LoggerFactory.getLogger(RecommendationReplayStore.class);

    /** 규칙으로 만든 응답의 표시. RouteRecommendServiceImpl이 쓰는 값과 같아야 한다 */
    private static final String PROVIDER_FALLBACK = "FALLBACK";

    private final RecommendationCacheDao recommendationCacheDao;
    private final ObjectMapper objectMapper;
    private final RouteProperties routeProperties;

    public RecommendationReplayStore(RecommendationCacheDao recommendationCacheDao,
                                     ObjectMapper objectMapper,
                                     RouteProperties routeProperties) {
        this.recommendationCacheDao = recommendationCacheDao;
        this.objectMapper = objectMapper;
        this.routeProperties = routeProperties;
    }

    /**
     * 같은 키로 이미 응답한 것이 있으면 꺼낸다.
     *
     * <b>요청 내용이 다르면 못 본 것으로 친다.</b> 클라이언트가 키를 잘못 재사용한 것인데, 결제와 달리 여기서 중복이 치명적이지 않으므로 에러를 내지 않고 새로 만들어 준다. 대신 로그에 남겨 클라이언트 버그를 잡을 수 있게 한다.
     *
     * @param clientKey Idempotency-Key 헤더 값. null이면 항상 비어 있는 값을 돌려준다
     */
    public Optional<RouteRecommendResponseDTO> replay(String clientKey, Long userId,
                                                      RouteRecommendRequestDTO request) {
        if (clientKey == null) {
            return Optional.empty();
        }
        String payload = recommendationCacheDao.selectValidPayload(
                IdempotencyKey.of(clientKey, userId));
        if (payload == null) {
            return Optional.empty();
        }
        try {
            Stored stored = objectMapper.readValue(payload, Stored.class);
            if (!sameRequest(stored.request(), request)) {
                log.warn("같은 Idempotency-Key에 다른 요청이 왔다. 새로 만든다");
                return Optional.empty();
            }
            return Optional.ofNullable(stored.response());
        } catch (JsonProcessingException e) {
            // 저장 형식이 바뀌었거나 깨진 것이다. 재현에 실패해도 추천은 계속돼야 한다
            log.warn("저장된 응답을 읽지 못했다. 새로 만든다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 응답을 저장해 재시도에 대비한다.
     *
     * <b>요청대로 처리되지 않은 응답은 저장하지 않는다.</b> 재시도로 같은 결과를 다시 받느니 새로 시도해 제대로 된 것을 받는 편이 낫다.
     *
     * <pre>
     * aiProvider == FALLBACK        LLM이 아니라 규칙이 만들었다
     * 회피를 요청했는데 적용 안 됨    사용자가 켠 안전 기능이 빠졌다
     * </pre>
     *
     * 회피 쪽이 특히 그렇다. 사고다발지가 유일한 통로라 실패하는 경우가 있는데 경유지 조합이 매번 달라 결정적이지 않다. 저장해 버리면 다음에 성공할 기회를 없앤다.
     *
     * @param avoidRequested 회피 도형을 실어 보냈는지. <b>요청 여부이지 결과가 아니다</b> -
     *                       결과만 보면 회피를 안 켠 경우와 구분되지 않는다
     */
    public void remember(String clientKey, Long userId,
                         RouteRecommendRequestDTO request, RouteRecommendResponseDTO response,
                         boolean avoidRequested) {
        if (clientKey == null) {
            return;
        }
        if (PROVIDER_FALLBACK.equals(response.getAiProvider())) {
            log.info("규칙으로 만든 응답이라 저장하지 않는다");
            return;
        }
        if (avoidRequested && !Boolean.TRUE.equals(response.getAvoidDangerZonesApplied())) {
            log.info("회피를 요청했는데 적용되지 않아 저장하지 않는다");
            return;
        }
        try {
            recommendationCacheDao.upsert(
                    IdempotencyKey.of(clientKey, userId),
                    objectMapper.writeValueAsString(new Stored(request, response)),
                    routeProperties.cacheTtlMinutes());
        } catch (JsonProcessingException e) {
            // 저장에 실패해도 응답은 이미 만들어졌다. 다음 재시도가 느려질 뿐이다
            log.warn("응답을 저장하지 못했다: {}", e.getMessage());
        }
    }

    /**
     * 두 요청이 같은지 본다.
     *
     * DTO 규약이 class + Lombok(@Getter @Setter @NoArgsConstructor @AllArgsConstructor)이라 {@code equals}가 없다. 그대로 비교하면 동일성 비교가 되어 영영 false이고, 그러면 재현이 아예 안 된다. 같은 클래스를 같은 매퍼로 직렬화하면 필드 순서가 같으므로 문자열로 견준다.
     */
    private boolean sameRequest(RouteRecommendRequestDTO a, RouteRecommendRequestDTO b)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(a).equals(objectMapper.writeValueAsString(b));
    }

    /**
     * 저장 형식.
     *
     * 요청을 함께 담는 이유는 같은 키에 다른 요청이 왔는지 가리기 위해서다. 응답만 담으면 클라이언트가 키를 잘못 재사용했을 때 엉뚱한 코스가 나간다.
     */
    private record Stored(RouteRecommendRequestDTO request, RouteRecommendResponseDTO response) {
    }
}
