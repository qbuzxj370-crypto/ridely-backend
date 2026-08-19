package kr.ridely.infra.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * LLM 호출 설정 바인딩. application.yml의 ridely.ai.llm.* 값을 주입받는다.
 *
 * 모델명은 여기 없다. spring.ai.google.genai.chat.options.model에 있고 Spring AI가 ChatClient에 자동 주입한다. 두 곳에 두면 어느 쪽이 적용됐는지 알 수 없어진다.
 *
 * temperature만 호출별로 갈라 둔 이유는 성격이 달라서다. 설계는 구조화 출력이라 낮아야 하고 코멘트는 자연어라 조금 풀어야 한다. 전역값 하나로는 둘 다 만족시킬 수 없다.
 *
 * @param primaryProvider       사용 중인 공급자. 전환 시 로그로 확인하는 용도다
 * @param timeoutSeconds        호출 타임아웃. 초과하면 실패시킨다. fallback은 아직 없다
 * @param maxRetry              재시도 횟수. 아직 사용하지 않는다
 * @param designTemperature     코스 설계 호출의 temperature
 * @param commentTemperature    코멘트 생성 호출의 temperature
 * @param toneViolationPatterns 톤 위반 판정 문구. 톤 검증기 도입 시 사용한다
 */
@ConfigurationProperties(prefix = "ridely.ai.llm")
public record LlmProperties(
        String primaryProvider,
        int timeoutSeconds,
        int maxRetry,
        double designTemperature,
        double commentTemperature,
        List<String> toneViolationPatterns
) {
}
