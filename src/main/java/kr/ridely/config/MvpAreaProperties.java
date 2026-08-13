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
}
