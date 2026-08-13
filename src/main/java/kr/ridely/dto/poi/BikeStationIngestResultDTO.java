package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 따릉이 대여소 적재 결과.
 * POST /api/v1/poc/seoul/stations/ingest 응답.
 *
 * 다음이 어긋나면 원본이나 매핑을 의심해야 한다.
 *   - 수집 건수가 3,237에서 크게 벗어난다
 *   - 좌표 결측이 0이 아니다
 *   - 비활성 전환이 갑자기 많다 (API가 일부만 돌려줬을 수 있다)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BikeStationIngestResultDTO {

    /** API에서 수집한 건수. 2026-08-12 기준 3,237 */
    private int fetchedCount;

    /** INSERT 또는 UPDATE된 건수. 대여소는 마스터라 재실행해도 0이 되지 않는다 */
    private int upsertedCount;

    /** 좌표가 없어 건너뛴 건수. 0이 정상 */
    private int skippedNoCoordinate;

    /** 테이블 전체 건수 */
    private int totalCount;

    /** 운영 중인 대여소 수 */
    private int activeCount;

    /**
     * 활성 상태가 바뀐 건수 (폐쇄 → 비활성, 재개 → 활성).
     * 첫 적재에서는 0이고, 이후 회차에서 값이 크면 API 응답이 불완전했는지 확인해야 한다.
     */
    private int activationChanged;
}
