package kr.ridely.infra.llm;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 구조화 출력 LLM 호출기.
 *
 * 설계와 코멘트 두 클라이언트가 공통으로 쓴다. 호출 규약이 같고 다른 것은 프롬프트 파일과 결과 타입뿐이라, 온도·타임아웃·오류 처리를 여기 모은다.
 *
 * 프롬프트 로딩과 치환은 ChatClient가 한다. 처음에는 파일을 직접 읽고 치환기를 만들었는데 전부 불필요했다. ChatClient의 user(u -&gt; u.text(resource).params(map)) 한 줄이 Resource 로딩·변수 치환·렌더링을 하고, call().entity(Type)이 구조화 출력까지 처리한다.
 *
 * 구조화 출력은 BeanOutputConverter가 JSON 스키마를 프롬프트에 덧붙이고 응답을 역직렬화하는 방식이다. 대상 클래스는 기본 생성자와 setter가 있어야 하며, 우리 DTO 규약(class + Lombok)이 그 조건을 만족한다.
 */
@Component
public class StructuredLlmCaller {

    private static final Logger log = LoggerFactory.getLogger(StructuredLlmCaller.class);

    /**
     * 치환자 구분자.
     *
     * 기본값 {} 를 쓰면 프롬프트에 중괄호가 들어오는 순간 파싱이 깨진다. 구조화 출력 스키마가 프롬프트에 덧붙는 구조라 중괄호가 들어올 여지가 상시로 있다. 공식 문서도 JSON을 넣을 계획이면 구분자를 바꾸라고 안내한다.
     */
    private static final char START_DELIMITER = '<';
    private static final char END_DELIMITER = '>';

    private final ChatClient chatClient;
    private final LlmProperties properties;

    /**
     * 프롬프트 렌더러.
     *
     * ⚠️ 검증을 WARN으로 낮춘 이유가 있다. 기본값 THROW는 "템플릿에 있는데 넘긴 값에 없는 이름"을 전부 미치환으로 보는데, StringTemplate 반복문의 지역 인자까지 그렇게 잡는다. 후보 목록을 &lt;tours:{c|...}&gt; 로 도는 순간 c가 미치환 변수로 걸려 렌더링 자체가 실패한다.
     *
     * 반복문을 포기하면 목록 조립이 Java로 돌아오므로 검증을 낮추는 쪽을 택했다. WARN이어도 값이 정말 빠지면 로그에는 남는다.
     */
    private final TemplateRenderer templateRenderer = StTemplateRenderer.builder()
            .startDelimiterToken(START_DELIMITER)
            .endDelimiterToken(END_DELIMITER)
            .validationMode(ValidationMode.WARN)
            .build();

    public StructuredLlmCaller(ChatClient.Builder chatClientBuilder, LlmProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    /**
     * 프롬프트 파일을 렌더링해 보내고 결과를 지정한 타입으로 받는다.
     *
     * 타임아웃은 CompletableFuture로 건다. Spring AI의 blocking 호출에는 호출별 타임아웃 지점이 없어서다.
     *
     * ⚠️ 시간이 초과돼도 아래 호출 자체는 계속 돈다. 응답이 와도 버려질 뿐이고 스레드는 그때까지 물려 있다. 지금은 실패를 빨리 알리는 것이 우선이라 이대로 두고, 호출 취소는 fallback 체계를 만들 때 함께 본다.
     *
     * @param purpose      로그에 남길 호출 목적. 어느 단계에서 실패했는지 구분한다
     * @param systemPrompt 페르소나 프롬프트 파일
     * @param userPrompt   지시문 프롬프트 파일. 치환자를 담는다
     * @param variables    치환자에 넣을 값
     * @param temperature  호출별 온도. 전역 설정을 덮어쓴다
     */
    public <T> T call(String purpose, Resource systemPrompt, Resource userPrompt,
                      Map<String, Object> variables, double temperature, Class<T> responseType) {

        long startedAt = System.currentTimeMillis();
        try {
            T result = CompletableFuture
                    .supplyAsync(() -> chatClient.prompt()
                            .system(s -> s.text(systemPrompt, StandardCharsets.UTF_8))
                            .user(u -> u.text(userPrompt, StandardCharsets.UTF_8).params(variables))
                            .templateRenderer(templateRenderer)
                            .options(ChatOptions.builder().temperature(temperature).build())
                            .call()
                            .entity(responseType))
                    .orTimeout(properties.timeoutSeconds(), TimeUnit.SECONDS)
                    .join();

            log.info("LLM {} 완료: {}ms (temperature {})",
                    purpose, System.currentTimeMillis() - startedAt, temperature);
            return result;

        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof TimeoutException) {
                log.error("LLM {} 타임아웃: {}초 초과", purpose, properties.timeoutSeconds());
            } else {
                // 구조화 출력 파싱 실패와 치환자 누락도 여기로 온다.
                // 원문을 보려면 org.springframework.ai 로그를 DEBUG로 올린다
                log.error("LLM {} 실패: {}", purpose, cause.getMessage(), cause);
            }
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }
}
