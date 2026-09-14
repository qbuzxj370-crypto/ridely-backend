package kr.ridely.infra.llm;

/**
 * LLM 호출이 실패했음을 알린다. HTTP 응답이 무엇이 될지는 정하지 않는다.
 *
 * {@link StructuredLlmCaller}가 {@code BusinessException(COMMON_500)}을 던지던 자리를 대신한다. 바꾼 이유는 호출부가 fallback으로 받아야 하기 때문이다. COMMON_500을 그대로 두고 호출부가 그것을 catch하면 진짜 서버 오류까지 함께 삼킨다 - DB 장애로 난 COMMON_500과 LLM 파싱 실패로 난 COMMON_500이 같은 타입이라 구분할 방법이 없다.
 *
 * 그래서 이 예외는 <b>계층을 가리키는 예외</b>다. "LLM 호출이 안 됐다"까지만 말하고, 그 다음에 무엇을 할지(재시도·fallback·에러 응답)는 호출부가 정한다.
 *
 * <b>이 예외가 사용자에게 그대로 나가는 경로는 없다.</b> CourseDesignClient는 알고리즘 fallback으로, CoachCommentClient는 템플릿 코멘트로 받는다. 둘 다 DB 후보만 쓰므로 실패할 여지가 없고, 후보가 0건인 경우는 그 앞에서 ROUTE-006으로 이미 걸러진다. 잡히지 않고 GlobalExceptionHandler까지 올라간다면 그것은 fallback 연결이 빠진 것이므로 500으로 드러나는 편이 맞다.
 *
 * @see StructuredLlmCaller
 */
public class LlmCallException extends RuntimeException {

    /** 실패한 호출의 목적. "코스 설계" / "코치 코멘트" 처럼 로그에 남기는 값이다 */
    private final String purpose;

    /** 타임아웃으로 끝났는지. 재시도 여부를 이 값으로 가른다 */
    private final boolean timeout;

    public LlmCallException(String purpose, boolean timeout, String message, Throwable cause) {
        super(message, cause);
        this.purpose = purpose;
        this.timeout = timeout;
    }

    public String getPurpose() {
        return purpose;
    }

    /**
     * 타임아웃이면 재시도하지 않는다.
     *
     * 이미 제한 시간을 다 쓴 뒤라 한 번 더 부르면 사용자 대기가 두 배가 된다. 목표 p50이 10초인데 타임아웃만 15초다. 즉시 fallback으로 넘기는 편이 빠르고, fallback은 LLM을 쓰지 않으므로 쿼터도 아낀다.
     */
    public boolean isTimeout() {
        return timeout;
    }
}
