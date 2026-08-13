package kr.ridely.infra.seoul;

import java.util.List;

/**
 * 서울 열린데이터광장 응답의 공통 형태.
 *
 * 서비스마다 JSON 루트 키가 다르지만(그것도 서비스명과 일치하지 않는 경우가 있다 —
 * {@code tbCycleStationInfo} → {@code stationInfo}) 그 아래 구조는 같다:
 * {@code list_total_count} · {@code RESULT{CODE,MESSAGE}} · {@code row[]}.
 *
 * 이 인터페이스를 두는 이유는 수집·검증 로직을 서비스마다 복사하지 않기 위해서다.
 * 1,000건 단위 페이지네이션, XML 오류 응답 판별, 결과 코드 검사는 전 서비스가 동일하다.
 *
 * @param <R> 행 타입
 */
public interface SeoulApiResponse<R> {

    /** "INFO-000"이면 정상, "INFO-200"이면 데이터 없음 */
    String resultCode();

    String resultMessage();

    /** 전체 결과 수. 페이지네이션 종료 판단에 쓴다 */
    int totalCount();

    /** 이번 응답의 행. 구조가 비정상이어도 null을 돌려주지 않는다 */
    List<R> rows();
}
