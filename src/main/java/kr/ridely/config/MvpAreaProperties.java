package kr.ridely.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MVP 서비스 범위 (bounding box).
 * application.yml의 ridely.mvp-area.* 값을 주입받는다.
 *
 * 한강 자전거길 서울 구간 — 아라한강갑문~잠실, 약 40km.
 * TourAPI·POI 배치 적재 범위 판정과 코스 추천 서비스영역 검사에 쓴다.
 *
 * 설정으로 둔 이유: 지역 확장 시 코드를 고치지 않고 범위만 넓히기 위해서다.
 *
 * @param minLat 남쪽 경계
 * @param maxLat 북쪽 경계
 * @param minLng 서쪽 경계
 * @param maxLng 동쪽 경계
 */
@ConfigurationProperties(prefix = "ridely.mvp-area")
public record MvpAreaProperties(
        double minLat,
        double maxLat,
        double minLng,
        double maxLng
) {

    /**
     * 좌표가 서비스 범위 안인지 본다.
     *
     * 추천은 출발지·도착지를, POI 조회는 지도 중심을 이 기준으로 가른다. 두 곳이 같은 판정을 써야 한다 - 추천은 되는데 주변 시설은 안 나오거나 그 반대가 되면 사용자가 원인을 짐작할 수 없다.
     *
     * ⚠️ <b>사각형이라 실제 서비스 구간보다 넓다.</b> 한강은 띠인데 경계 상자는 동서 31km · 남북 12.2km라 네 모서리가 내륙으로 남는다. 범위 안이어도 주변에 아무것도 없는 지점이 있다.
     */
    public boolean contains(double lng, double lat) {
        return lng >= minLng && lng <= maxLng
                && lat >= minLat && lat <= maxLat;
    }
}
