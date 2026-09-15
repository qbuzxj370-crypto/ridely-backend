package kr.ridely.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멱등성 키 변환 단위 테스트.
 *
 * <b>여기서 고정하려는 것은 「남의 키로 남의 추천을 못 본다」다.</b> 클라이언트가 보낸 값을 그대로 쓰면 다른 사용자가 같은 문자열을 보냈을 때 남의 코스가 나간다. UUID라 우연히 겹칠 일은 없지만 값을 만드는 것은 클라이언트이고 우리는 그것을 신뢰할 근거가 없다.
 */
class IdempotencyKeyTest {

    private static final String CLIENT_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    @DisplayName("사용자가 다르면 키가 다르다")
    void differentUserGivesDifferentKey() {
        assertThat(IdempotencyKey.of(CLIENT_KEY, 7L))
                .isNotEqualTo(IdempotencyKey.of(CLIENT_KEY, 8L));
    }

    @Test
    @DisplayName("비회원과 회원이 같은 값을 보내도 키가 다르다")
    void guestAndMemberDoNotCollide() {
        assertThat(IdempotencyKey.of(CLIENT_KEY, null))
                .isNotEqualTo(IdempotencyKey.of(CLIENT_KEY, 7L));
    }

    @Test
    @DisplayName("같은 사용자가 같은 값을 보내면 같은 키다")
    void sameInputGivesSameKey() {
        assertThat(IdempotencyKey.of(CLIENT_KEY, 7L))
                .isEqualTo(IdempotencyKey.of(CLIENT_KEY, 7L));
    }

    @Test
    @DisplayName("사용자 번호와 클라이언트 키의 경계가 섞이지 않는다")
    void boundaryIsNotAmbiguous() {
        // 구분자가 없으면 (1, "23...")과 (12, "3...")이 같은 문자열이 된다.
        // 사용자 번호는 자릿수가 제각각이라 실제로 일어날 수 있다
        assertThat(IdempotencyKey.of("23abc", 1L))
                .isNotEqualTo(IdempotencyKey.of("3abc", 12L));
    }

    @Test
    @DisplayName("길이가 64자로 고정된다")
    void keyFitsColumn() {
        // cache_key가 VARCHAR(64)다. 클라이언트가 보내는 값의 길이를 우리가 정하지 않으므로
        // 그대로 쓰면 컬럼을 넘길 수 있다
        assertThat(IdempotencyKey.of(CLIENT_KEY, 7L)).hasSize(64);
        assertThat(IdempotencyKey.of("x".repeat(500), null)).hasSize(64);
    }
}
