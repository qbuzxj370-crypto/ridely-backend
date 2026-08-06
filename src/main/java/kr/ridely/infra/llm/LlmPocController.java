package kr.ridely.infra.llm;

import kr.ridely.common.ApiResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — Spring AI 1.1.6 + Google Gen AI Starter 동작 검증용.
 * GET /api/v1/poc/llm
 *
 * 검증 기준 (SPRINT_W1 C5):
 *   ChatClient가 Gemini 응답을 정상 수신하면 성공.
 *
 * Fallback: Google Gen AI Starter 이슈 시 vertex-ai-gemini Starter로 전환 (ADR-003).
 * 검증 완료 후: LlmClient 인터페이스 추상화(2~3주차)로 발전, 이 컨트롤러는 삭제.
 *
 * GOOGLE_API_KEY가 없으면 등록 자체를 건너뛴다 (로컬에서 AI 기능 없이 기동 가능하도록).
 */
@RestController
@RequestMapping("/api/v1/poc/llm")
@ConditionalOnProperty(name = "GOOGLE_API_KEY")
public class LlmPocController {

    private final ChatClient chatClient;

    // ChatClient.Builder는 Spring AI가 자동 구성해 주입
    public LlmPocController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping
    public ApiResponse<String> llmPoc() {
        String reply = chatClient.prompt()
                .user("안녕하세요, 라이딩 코치님. 한 문장으로 인사해 주세요.")
                .call()
                .content();
        return ApiResponse.ok(reply);
    }
}
