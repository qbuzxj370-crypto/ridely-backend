package kr.ridely;

import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션 컨텍스트가 뜨는지만 본다.
 *
 * <b>AbstractIntegrationTest를 상속한다.</b> Spring Initializr가 만든 원래 형태는 {@code @SpringBootTest}만 붙어 있어 DB도 설정도 없이 기동을 시도했다. 개발 PC에는 환경변수가 있어 통과했지만 CI 러너에서는 {@code ${JWT_SECRET}} 해석부터 실패한다.
 *
 * 다른 통합 테스트도 같은 컨텍스트를 띄우므로 중복으로 보이지만, <b>실패했을 때 원인이 바로 드러나는 것이 이 테스트의 값어치다.</b> 빈이 충돌했을 때 여기서는 「컨텍스트 로딩 실패」 하나로 끝나는데, 다른 테스트에서는 로그인 실패나 조회 결과 없음 같은 증상으로 나타나 원인을 거슬러 올라가야 한다.
 */
class RidelyApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}
