package kr.ridely.infra.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

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

    private static final Logger log = LoggerFactory.getLogger(LlmCallBudget.class);

    /**
     * 자정을 서버 타임존이 아니라 한국 시간으로 고정한다.
     *
     * {@code LocalDate.now()}(타임존 없이)를 썼다면 EC2 JVM 기본 타임존을 그대로 탄다.
     * 실측해보니 EC2가 UTC라, 그 상태로 두면 리셋이 KST 오전 9시에 일어난다 - 전날 한도를
     * 소진했으면 자정이 지나고도 오전 9시까지 계속 fallback으로 샌다. 시연이 오전이면
     * 바로 이 문제를 겪는다.
     */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 이 비율을 넘으면 소진되기 전에 경고를 한 번 남긴다 (시연 준비 중 조기 감지용) */
    private static final double WARNING_THRESHOLD = 0.8;

    private final int dailyLimit;
    private int count = 0;
    private LocalDate windowDate = LocalDate.now(KST);
    private boolean warningLogged = false;

    public LlmCallBudget(LlmProperties properties) {
        this.dailyLimit = properties.dailyCallLimit();
    }

    /**
     * 호출 가능하면 카운트를 올리고 {@code true}, 오늘 한도를 이미 다 썼으면
     * 카운트를 올리지 않고 {@code false}를 반환한다.
     */
    public synchronized boolean tryAcquire() {
        resetIfNewDay();

        if (count >= dailyLimit) {
            return false;
        }
        count++;

        if (!warningLogged && count >= dailyLimit * WARNING_THRESHOLD) {
            warningLogged = true;
            log.warn("LLM 일일 호출 한도 {}% 도달: {}/{}",
                    (int) (WARNING_THRESHOLD * 100), count, dailyLimit);
        }
        return true;
    }

    /** 오늘 사용량. 리뷰에서 요청된 관측용 — 초과 로그에 used/limit을 같이 남기기 위함 */
    public synchronized int used() {
        resetIfNewDay();
        return count;
    }

    public int limit() {
        return dailyLimit;
    }

    private void resetIfNewDay() {
        LocalDate today = LocalDate.now(KST);
        if (!today.equals(windowDate)) {
            windowDate = today;
            count = 0;
            warningLogged = false;
        }
    }
}
