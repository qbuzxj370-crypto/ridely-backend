package kr.ridely.infra.llm;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
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
     * StringTemplate 반복문의 지역 인자 이름.
     *
     * 검증기는 "템플릿에 있는데 넘긴 값에 없는 이름"을 미치환으로 보는데, 반복문의 지역 인자까지 그렇게 잡는다. 후보 목록을 &lt;tours:{c|...}&gt; 로 도는 순간 c가 걸린다.
     *
     * 그래서 같은 이름을 빈 값으로 함께 넘긴다. 하위 템플릿의 형식 인자는 그 범위 안에서 전역 값을 가리므로 렌더링 결과는 달라지지 않고, 검증기의 "넘긴 값" 목록에만 이름이 올라간다.
     *
     * ⚠️ 반복문 인자는 반드시 이 목록에 있는 이름을 쓴다. 새 이름을 쓰면 렌더링이 실패한다 — 검증이 THROW이기 때문이다. 실패하는 편이 낫다고 본 이유는 아래 렌더러 주석에 있다.
     */
    private static final List<String> LOOP_PARAMETERS = List.of("c", "w", "z");

    /**
     * 프롬프트 렌더러.
     *
     * 검증은 기본값 THROW를 쓴다. 치환자가 빠지면 빈 문자열로 렌더링되는데, 그러면 LLM은 불완전한 지시를 받고도 그럴듯한 결과를 계속 내놓는다. "약 km를 더 채워야 합니다" 같은 문장이 나가도 응답만 봐서는 알 수 없다. 조용한 품질 저하보다 즉시 실패가 낫다.
     *
     * 한때 이 검증을 WARN으로 낮춰 뒀었다. 반복문 지역 인자가 미치환으로 잡혀 렌더링이 통째로 실패했기 때문이다. LOOP_PARAMETERS로 그 원인을 없앴으므로 기본값으로 되돌린다.
     */
    private final TemplateRenderer templateRenderer = StTemplateRenderer.builder()
            .startDelimiterToken(START_DELIMITER)
            .endDelimiterToken(END_DELIMITER)
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
        Map<String, Object> params = withLoopParameters(variables);
        try {
            T result = CompletableFuture
                    .supplyAsync(() -> chatClient.prompt()
                            .system(s -> s.text(systemPrompt, StandardCharsets.UTF_8))
                            .user(u -> u.text(userPrompt, StandardCharsets.UTF_8).params(params))
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

    /**
     * 반복문 지역 인자를 빈 값으로 덧붙인다.
     *
     * 호출부의 맵을 건드리지 않으려고 새로 만든다. 호출부가 넘긴 값이 우선이므로 같은 이름을 진짜 변수로 쓰더라도 덮이지 않는다.
     */
    private Map<String, Object> withLoopParameters(Map<String, Object> variables) {
        Map<String, Object> params = new LinkedHashMap<>();
        LOOP_PARAMETERS.forEach(name -> params.put(name, ""));
        params.putAll(variables);
        return params;
    }
}
