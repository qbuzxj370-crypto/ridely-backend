package kr.ridely.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 설정.
 * 외부 API(ORS, Kakao, 공공데이터) 호출용 공통 WebClient Bean.
 *
 * 각 infra 클라이언트(infra/ors, infra/kakao, infra/publicdata)가 주입받아 사용.
 * 도메인별 baseUrl은 각 클라이언트에서 설정.
 */
@Configuration
public class WebClientConfig {

    /**
     * 외부 API 호출용 공통 빌더.
     *
     * <b>DNS 해석을 운영체제에 맡긴다.</b> Netty는 기본적으로 자체 리졸버로 UDP DNS 질의를 직접 보내는데, 그 리졸버가 OS의 DNS 설정을 그대로 따르지 않는다. 그래서 같은 호스트를 curl은 풀고 애플리케이션만 못 푸는 상황이 생긴다.
     *
     * 실제로 겪었다 - 2026-09-08에 추천이 UnknownHostException으로 죽었고 원인은 `api.heigit.org` 질의가 SERVFAIL을 받은 것이었다. 같은 시각 curl은 성공했다. 직전 로그에 시계가 3분 넘게 건너뛴 기록(HikariPool clock leap)이 있어, PC가 절전에서 깨어나며 Netty의 DNS 상태가 어긋난 것으로 보인다.
     *
     * {@link DefaultAddressResolverGroup}은 JDK의 InetAddress를 쓴다. curl과 같은 경로라 OS의 DNS 서버·캐시·hosts 파일을 그대로 따른다.
     *
     * <b>이 빌더를 네 클라이언트가 공유한다</b>(ORS·TourAPI·서울시·TAAS). 한 곳을 고치면 전부 적용된다.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // TODO: 타임아웃, 커넥션 풀, 로깅 필터 등 공통 설정 추가
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
