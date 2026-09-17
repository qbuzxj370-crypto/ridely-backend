package kr.ridely.infra.llm;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * LLM(Bedrock 등) 호출의 하루 총량을 제한한다. 사용자별이 아니라 <b>서비스 전체 합산</b>이다.
 *
 * 테스트 단계에서 AWS 비용 폭주를 막기 위한 안전장치다. IP당 요청 제한과는 층이 다르다 —
 * 그쪽은 "한 명이 혼자 폭주"를 막고, 이건 "여러 명이 정상적으로 나눠 써도 총합이 예산을
 * 넘지 않게"를 막는다.
 *
 * 한도를 넘으면 {@link StructuredLlmCaller}가 {@link LlmCallException}을 받아 기존
 * fallback 경로(규칙 기반 설계·템플릿 코멘트)로 넘어간다. 새 실패 모드가 아니라 이미 있는
 * 안전망을 재사용하는 것이다.
 *
 * 자정 리셋은 별도 스케줄러 없이 호출 시점에 날짜가 바뀌었으면 그때 리셋한다 (지연 리셋 —
 * 트래픽 없는 새벽에 스케줄러를 따로 깨울 이유가 없다).
 */
@Component
public class LlmCallBudget {

    private final int dailyLimit;
    private int count = 0;
    private LocalDate windowDate = LocalDate.now();

    public LlmCallBudget(LlmProperties properties) {
        this.dailyLimit = properties.dailyCallLimit();
    }

    /**
     * 호출 가능하면 카운트를 올리고 {@code true}, 오늘 한도를 이미 다 썼으면
     * 카운트를 올리지 않고 {@code false}를 반환한다.
     */
    public synchronized boolean tryAcquire() {
        LocalDate today = LocalDate.now();
        if (!today.equals(windowDate)) {
            windowDate = today;
            count = 0;
        }
        if (count >= dailyLimit) {
            return false;
        }
        count++;
        return true;
    }
}
