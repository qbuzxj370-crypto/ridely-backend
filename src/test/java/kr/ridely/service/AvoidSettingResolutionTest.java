package kr.ridely.service;

import kr.ridely.dao.UserSettingsDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사고다발지 회피 설정 해석 단위 테스트.
 *
 * DB 없이 순수 판정만 본다. private 메서드를 리플렉션으로 부르는 방식은 AccidentZoneDangerLevelTest의 선례를 따랐다.
 *
 * 여기서 고정하려는 것은 <b>회원 설정이 요청 헤더를 이긴다</b>는 규칙이다. 설정 화면에서 끈 것을 헤더로 되살릴 수 있으면 설정의 의미가 없다. 반대로 회원이 설정을 켰는데 헤더가 꺼져 있다고 회피가 꺼져서도 안 된다.
 *
 * UserSettingsDao가 인터페이스라 람다로 흉내 낸다. 목 라이브러리를 들이지 않는다.
 */
class AvoidSettingResolutionTest {

    private static final long USER_ID = 7L;

    @Test
    @DisplayName("비회원은 헤더를 따른다")
    void anonymousFollowsHeader() {
        assertThat(requested(null, true, settings -> null)).isTrue();
        assertThat(requested(null, false, settings -> null)).isFalse();
    }

    @Test
    @DisplayName("비회원이 헤더를 보내지 않으면 회피하지 않는다")
    void anonymousWithoutHeaderDoesNotAvoid() {
        // 기본값이 켜짐이면 사용자가 요청하지 않은 우회가 생기고 거리가 늘어난다.
        // 스키마의 avoid_danger_zones DEFAULT FALSE와 같은 방향이다
        assertThat(requested(null, null, settings -> null)).isFalse();
    }

    @Test
    @DisplayName("회원은 저장된 설정을 따르고 헤더를 무시한다")
    void memberSettingBeatsHeader() {
        // 설정 화면에서 끈 것을 헤더로 되살릴 수 있으면 설정의 의미가 없다
        assertThat(requested(USER_ID, true, settings -> false)).isFalse();
        // 반대 방향도 막는다. 켠 설정을 헤더가 끄지 못한다
        assertThat(requested(USER_ID, false, settings -> true)).isTrue();
    }

    @Test
    @DisplayName("회원 설정 행이 없으면 끈 것으로 본다")
    void missingSettingRowMeansOff() {
        // 가입 트랜잭션이 기본값 행을 만들므로 정상 회원에게는 없을 수 없다.
        // 데이터가 어긋난 상황인데, 여기서 켠 것으로 보면 사용자가 켠 적 없는
        // 우회가 생긴다. 끈 쪽이 덜 놀랍다
        assertThat(requested(USER_ID, true, settings -> null)).isFalse();
    }

    /**
     * @param avoidHeader 비회원 헤더 값
     * @param dao         저장된 설정을 흉내 낸다. null을 돌려주면 설정 행이 없는 상황이다
     */
    private boolean requested(Long userId, Boolean avoidHeader, UserSettingsDao dao) {
        // 인자 13개 중 이 판정에 쓰이는 것은 UserSettingsDao(11번째) 하나다.
        // 나머지를 null로 두면 다른 협력자를 건드리는 순간 바로 드러난다
        RouteRecommendServiceImpl service = new RouteRecommendServiceImpl(
                null, null, null, null, null, null, null, null, null, null, dao, null, null);
        // 인자를 배열로 감싼다. 둘 다 null인 경우가 있어 가변인자 해석이 흔들릴 여지를 없앤다
        return (boolean) ReflectionTestUtils.invokeMethod(
                service, "isAvoidRequested", new Object[]{userId, avoidHeader});
    }
}
