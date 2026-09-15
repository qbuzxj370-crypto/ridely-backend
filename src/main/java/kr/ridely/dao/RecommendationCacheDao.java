package kr.ridely.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 추천 응답 멱등성 저장소 (ADR-002: 공간 연산이 없는 CRUD -&gt; MyBatis).
 *
 * <b>이름은 캐시지만 하는 일은 멱등성 키다.</b> 테이블이 {@code recommendation_cache}로 이미 만들어져 있어 그 이름을 쓴다.
 *
 * 요청 조건(좌표·우선순위·목표 거리)으로 키를 만들지 않는다. 클라이언트가 보낸 {@code Idempotency-Key}를 쓴다. 조건으로 만들면 「재시도인가 새 요청인가」를 우리가 추측해야 하는데, GPS 오차가 ±5~20m라 같은 자리에서도 좌표가 흔들리고 재추천은 조건이 같아 캐시에 갇힌다. 클라이언트가 명시하면 추측할 일이 없다.
 *
 * 히트하는 경우는 사실상 <b>네트워크 재시도</b>다. 추천 응답이 10초라 모바일에서 실재하고, 그 한 번이 LLM을 2~4회 더 쓴다.
 */
@Mapper
public interface RecommendationCacheDao {

    /**
     * 만료되지 않은 응답을 찾는다.
     *
     * 만료 판정을 SQL에서 하는 이유는 애플리케이션 시계를 믿지 않기 위해서다. Docker Desktop의 VM 시계가 호스트 절전 후 튀어 테스트가 깨진 적이 있다. 저장할 때도 {@code NOW()}를 쓰므로 기준이 하나로 유지된다.
     *
     * @return 응답 JSON 문자열. 없거나 만료됐으면 null
     */
    String selectValidPayload(@Param("cacheKey") String cacheKey);

    /**
     * 응답을 저장한다. 같은 키가 있으면 덮어쓴다.
     *
     * 덮어쓰기로 둔 이유는 만료된 행이 저절로 정리되게 하기 위해서다. 만료 뒤 같은 키가 다시 오는 일은 드물지만, 그때 삽입이 UNIQUE 제약에 걸리면 멀쩡한 요청이 500으로 죽는다.
     *
     * @param ttlMinutes 만료까지의 분. expires_at은 DB가 NOW()로 계산한다
     */
    int upsert(@Param("cacheKey") String cacheKey,
               @Param("payload") String payload,
               @Param("ttlMinutes") int ttlMinutes);
}
