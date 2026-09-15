-- ============================================================
-- V4 : recommendation_cache 주석을 멱등성 저장소로 정정
-- ============================================================
--
-- 배경
--   V1은 이 테이블을 "동일 요청 결과 캐싱"으로 두고 키를 요청 조건에서 뽑기로 했다.
--     COMMENT ON COLUMN recommendation_cache.cache_key IS
--       'SHA256(start + end + priorities + targetDistance + avoid_danger_zones)';
--
--   구현하면서 조건 기반을 버렸다. 두 가지가 걸렸다.
--     같은 자리에서도 좌표가 흔들린다   GPS 오차가 ±5~20m라 조건이 매번 미세하게 다르다.
--                                      재시도인데 새 요청으로 읽혀 LLM을 다시 부른다.
--     재추천이 갇힌다                   같은 조건으로 다시 눌러도 조건이 같으므로
--                                      20분 동안 같은 코스만 돌아온다.
--
--   그래서 클라이언트가 보낸 Idempotency-Key 헤더를 키로 쓴다. 재시도인지 새 요청인지
--   우리가 추측하지 않고 클라이언트가 명시한다.
--
-- 영향
--   테이블 구조와 인덱스는 그대로다. 바뀐 것은 키를 만드는 규칙뿐이라 주석만 고친다.
--   실제 계산은 kr.ridely.common.IdempotencyKey 에 있다.
-- ============================================================

COMMENT ON TABLE recommendation_cache IS
    '추천 응답 멱등성 저장소. 네트워크 재시도가 LLM을 다시 부르지 않게 한다.
     이름은 캐시지만 조건 기반 캐시가 아니다 - 클라이언트가 보낸 키로만 재현한다.';

COMMENT ON COLUMN recommendation_cache.cache_key IS
    'SHA256(user_id + "|" + Idempotency-Key 헤더). 비회원은 user_id 자리가 빈 문자열.
     사용자를 섞는 이유는 다른 사용자가 같은 키를 보냈을 때 남의 추천이 나가지 않게 하려는 것이고,
     해싱하는 이유는 헤더 값의 길이를 우리가 정하지 않기 때문이다(VARCHAR(64) = SHA256 hex).';

COMMENT ON COLUMN recommendation_cache.response_payload IS
    '{"request": 요청 DTO, "response": 응답 DTO}. 요청을 함께 담는 이유는 같은 키에 다른 요청이
     왔는지 가리기 위해서다 - 응답만 담으면 키를 잘못 재사용했을 때 엉뚱한 코스가 나간다.';

COMMENT ON COLUMN recommendation_cache.expires_at IS
    'NOW() + ridely.route.cache-ttl-minutes. 조회가 이 값으로 만료를 거르고,
     만료된 행은 지우지 않고 남겨 둔다(같은 키가 다시 오면 ON CONFLICT로 덮어쓴다).';
