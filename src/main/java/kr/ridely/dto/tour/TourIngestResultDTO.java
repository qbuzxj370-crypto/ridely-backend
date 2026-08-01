package kr.ridely.dto.tour;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TourAPI 적재 결과 요약.
 *
 * 적재 트리거 응답으로 사용한다. 개발계정 트래픽이 오퍼레이션당 일 1,000건이므로
 * 호출 횟수(apiCallCount)를 함께 반환해 잔여 한도를 가늠할 수 있게 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourIngestResultDTO {

    /** TourAPI 호출 횟수 (일일 한도 관리용) */
    private int apiCallCount;

    /** 응답으로 받은 항목 수 */
    private int receivedCount;

    /** DB에 저장된 수 (신규 + 갱신) */
    private int upsertedCount;

    /** 좌표 없음 등으로 건너뛴 수 */
    private int skippedCount;

    /** 적재 후 tour_attraction 전체 행 수 */
    private int totalRowCount;
}
