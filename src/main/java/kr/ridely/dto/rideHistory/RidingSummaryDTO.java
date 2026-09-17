package kr.ridely.dto.rideHistory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 라이딩 누적 통계 (GET /api/v1/riding-sessions/summary).
 *
 * 마이페이지와 저장 경로 화면이 쓴다. 목록을 받아 화면에서 합산하려면 기록 전체를 내려받아야 해서 서버가 센다.
 *
 * <h3>종료된 세션만 센다</h3>
 *
 * 달리는 중인 세션({@code ended_at IS NULL})은 거리·속도가 아직 없다. 횟수에만 넣으면 평균이 실제보다 낮아 보인다.
 *
 * <h3>강도별 횟수의 합이 totalRideCount보다 작을 수 있다</h3>
 *
 * 강도는 추천 코스가 가진 값이라 자유 주행에는 없다. 네 등급의 합과 총 횟수의 차이가 자유 주행 횟수다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RidingSummaryDTO {

    /** 종료된 라이딩 횟수. 기록이 없으면 0 */
    private long totalRideCount;

    /** 누적 주행 거리(km). 기록이 없으면 0 */
    private BigDecimal totalDistanceKm;

    /**
     * 평균 속도(km/h). 소수 첫째 자리까지.
     *
     * <b>null일 수 있고, 그때는 응답 JSON에서 필드가 빠진다</b>({@code default-property-inclusion: non_null}). 종료된 세션이 없거나, 있어도 속도를 보내지 않은 경우다. 0으로 채우면 「시속 0km로 달렸다」로 읽힌다.
     */
    private BigDecimal avgSpeedKmh;

    /** 가볍게 */
    private long lightCount;

    /** 적당히 */
    private long moderateCount;

    /** 끌어올림 */
    private long hardCount;

    /** 도전 */
    private long challengeCount;
}
