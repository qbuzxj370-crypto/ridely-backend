package kr.ridely.infra.llm;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 구조화 출력 LLM 호출기.
 *
 * 두 클라이언트(설계·코멘트)가 공통으로 쓴다. 호출 규약이 같고 다른 것은 프롬프트와 결과 타입뿐이라, 온도·타임아웃·오류 처리를 여기 모은다.
 *
 * 구조화 출력은 ChatClient의 entity(Class)를 쓴다. 내부적으로 BeanOutputConverter가 JSON 스키마를 프롬프트에 덧붙이고 응답을 역직렬화한다. 대상 클래스는 기본 생성자와 setter가 있어야 하며, 우리 DTO 규약(class + Lombok)이 그 조건을 만족한다.
 */
@Component
public class StructuredLlmCaller {

    private static final Logger log = LoggerFactory.getLogger(StructuredLlmCaller.class);

    private final ChatClient chatClient;
    private final LlmProperties properties;

    public StructuredLlmCaller(ChatClient.Builder chatClientBuilder, LlmProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    /**
     * 프롬프트를 보내고 결과를 지정한 타입으로 받는다.
     *
     * 타임아웃은 CompletableFuture로 건다. Spring AI의 blocking 호출에는 호출별 타임아웃 지점이 없어서다.
     *
     * ⚠️ 시간이 초과돼도 아래 호출 자체는 계속 돈다. 응답이 와도 버려질 뿐이고 스레드는 그때까지 물려 있다. 지금은 실패를 빨리 알리는 것이 우선이라 이대로 두고, 호출 취소는 fallback 체계를 만들 때 함께 본다.
     *
     * @param purpose     로그에 남길 호출 목적. 어느 단계에서 실패했는지 구분한다
     * @param temperature 호출별 온도. 전역 설정을 덮어쓴다
     */
    public <T> T call(String purpose, String systemPrompt, String userPrompt,
                      double temperature, Class<T> responseType) {
        long startedAt = System.currentTimeMillis();
        try {
            T result = CompletableFuture
                    .supplyAsync(() -> chatClient.prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
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
                // 구조화 출력 파싱 실패도 여기로 온다. 원문을 보려면 org.springframework.ai 로그를 DEBUG로 올린다
                log.error("LLM {} 실패: {}", purpose, cause.getMessage(), cause);
            }
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }
}
