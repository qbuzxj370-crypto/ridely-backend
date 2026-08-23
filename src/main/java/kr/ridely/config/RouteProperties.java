package kr.ridely.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 코스 추천 설정 바인딩. application.yml의 ridely.route.* 값을 주입받는다.
 *
 * 후보 수집은 반경도 개수도 목표 거리에서 유도한다. 고정값을 쓰면 짧은 코스에서는 과잉 수집하고 긴 코스에서는 후반부에 배치할 후보가 없다. 여기 있는 값들은 그 유도식의 계수와 상하한이다.
 *
 * @param candidateMinRadiusM      후보 수집 반경 하한 (m). 한강 폭에서 나온 값이다
 * @param candidateRadiusKm        후보 수집 반경 상한 (km)
 * @param candidatePerKm           거리 1km당 후보 수. 타입별로 다르다
 * @param candidateMinCount        타입별 후보 개수 하한
 * @param candidateMaxCount        타입별 후보 개수 상한. 프롬프트 토큰 예산이다
 * @param cacheTtlHours            추천 결과 캐시 수명. 캐시 도입 시 사용한다
 * @param maxTargetDistanceKm      목표 거리 상한. ROUTE-002 판정에 쓴다
 * @param dangerZoneAlertDistanceM 라이딩 모드 근접 알림 거리. 라이딩 모드 도입 시 사용한다
 * @param avoidDangerLevels        회피 대상 위험 등급. 회피를 켠 사용자에게만 적용된다
 */
@ConfigurationProperties(prefix = "ridely.route")
public record RouteProperties(
        int candidateMinRadiusM,
        double candidateRadiusKm,
        CandidatePerKm candidatePerKm,
        int candidateMinCount,
        int candidateMaxCount,
        int cacheTtlHours,
        double maxTargetDistanceKm,
        int dangerZoneAlertDistanceM,
        List<String> avoidDangerLevels
) {

    /**
     * 타입별 "거리 1km당 후보 수".
     *
     * 타입마다 다른 이유는 역할이 달라서다. 관광지는 코스의 목적지라 선택지가 넉넉해야 하고, 수리소·따릉이는 지나는 김에 들르는 곳이라 가까운 몇 개면 된다.
     */
    public record CandidatePerKm(
            double tour,
            double water,
            double repairShop,
            double bikeStation
    ) {
    }

    /** 목표 거리에 비례한 후보 개수. 하한·상한으로 자른다 */
    public int candidateCount(double perKm, double targetDistanceKm) {
        int derived = (int) Math.round(perKm * targetDistanceKm);
        return Math.clamp(derived, candidateMinCount, candidateMaxCount);
    }
}
