package kr.ridely.infra.llm;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * google-genai 클라이언트를 직접 만든다. 전송 계층 타임아웃을 걸기 위해서다.
 *
 * <b>왜 직접 만드나.</b> Spring AI 1.1.6에는 호출별 타임아웃이 없다 (spring-ai#4103이 아직 열려 있다). ChatClient에 넘긴 timeout은 조용히 무시되고 생성 시점 값만 먹는다 (spring-ai#6604). 프로퍼티로도 못 건다 (spring-ai#5400). 남은 경로가 {@code com.google.genai.Client}를 우리가 만들어 넣는 것뿐이다. 오토컨피그가 {@code @ConditionalOnMissingBean}이라 이 빈이 있으면 물러난다.
 *
 * <b>지금은 상한이 아예 없다.</b> google-genai 1.37.0의 ApiClient.createHttpClient가 OkHttp의 기본 10초를 일부러 꺼 둔다 - LLM 응답이 10초를 넘으니 그렇게 했다. connect·read·write가 전부 0(무한)이고 callTimeout도 OkHttp 기본이 0이다. 이 빈이 없으면 응답이 안 오는 호출은 서버가 끊을 때까지 산다.
 *
 * <pre>
 * builder.connectTimeout(Duration.ofMillis(0));
 * builder.readTimeout(Duration.ofMillis(0));
 * builder.writeTimeout(Duration.ofMillis(0));
 * httpOptions.timeout().ifPresent(t -> builder.callTimeout(Duration.ofMillis(t)));
 * </pre>
 *
 * <b>HttpOptions.timeout은 callTimeout에 붙는다.</b> connect나 read가 아니다. 우리에게는 그편이 낫다 - readTimeout은 바이트 사이 간격이라 서버가 조금씩 흘려보내면 무한정 늘어나지만, callTimeout은 DNS·연결·전송·응답 전체의 벽시계라 총량을 자른다. SDK가 기본으로 붙이는 RetryInterceptor의 재시도까지 이 안에 든다.
 *
 * <b>StructuredLlmCaller의 orTimeout과 역할이 다르다.</b> 이쪽은 발동하면 OkHttp가 콜을 취소하고 소켓을 닫아 스레드와 RPM을 회수한다. orTimeout은 CompletableFuture만 포기할 뿐 호출은 계속 돈다. 그래서 orTimeout을 더 뒤에 두어 보조 타임아웃으로 쓴다 - 같은 값이면 둘이 경합해 어느 예외가 올지 알 수 없다.
 *
 * ⚠️ 이 빈을 두면서 API 키 주입을 오토컨피그에서 넘겨받았다. Vertex 모드로 전환한다면 {@code Client.builder().project(..).location(..).vertexAI(true)} 분기를 여기에 직접 써야 한다.
 *
 * <b>{@code proxyBeanMethods = false}인 이유.</b> 이 클래스가 다른 빈보다 일찍 만들어져 CGLIB 프록시를 씌울 시점을 놓치고, 기본값으로 두면 기동할 때마다 그 사실이 경고로 찍힌다({@code Cannot enhance @Configuration bean definition}). 여기 {@code @Bean}은 하나뿐이고 빈끼리 부르는 곳이 없어 프록시가 할 일이 없으므로 명시해서 끈다.
 *
 * ⚠️ <b>{@code @Bean}을 하나 더 넣고 서로 부르면 그때는 매번 새 인스턴스가 만들어진다.</b> 프록시가 가로채 주지 않기 때문이다. 그런 호출이 필요해지면 메서드 인자로 주입받아야 한다.
 *
 * <b>prod에서는 로드하지 않는다.</b> AWS 마이그레이션 이후 prod는 Bedrock(Claude)을 쓴다
 * ({@code spring.ai.model.chat=bedrock-converse}, application-prod.yml). 이 클래스는
 * {@code spring.ai.google.genai.api-key}를 필수로 요구하는 수동 {@code @Bean}이라 —
 * Spring AI의 {@code spring.ai.model.chat} 셀렉터로 GoogleGenAiChatAutoConfiguration이
 * 안 켜져도 이 빈은 별개로 인스턴스화를 시도한다 — {@code @Profile}로 직접 막아야 한다.
 * 안 막으면 prod 기동이 플레이스홀더 해석 실패로 죽는다 (로컬에서 이미 같은 유형의 장애를 겪었다).
 */
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
public class GenAiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GenAiClientConfig.class);

    private static final int MS_PER_SECOND = 1000;

    @Bean
    public Client genAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                              LlmProperties properties) {
        int timeoutMs = properties.timeoutSeconds() * MS_PER_SECOND;

        log.info("google-genai 클라이언트를 직접 만든다. callTimeout={}ms", timeoutMs);

        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(timeoutMs)
                        .build())
                .build();
    }
}
