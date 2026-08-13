package kr.ridely.infra.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터 원본 파일 경로 설정.
 * application.yml의 ridely.seed.* 값을 주입받는다.
 *
 * 이 파일들은 저장소에 커밋하지 않는다(db/seed/는 .gitignore 대상).
 * 각자 공공데이터포털에서 내려받아 배치하며, 방법은 docs/shared/DATA_SOURCES.md 3장에 있다.
 *
 * 경로를 코드에 박지 않고 설정으로 분리한 이유:
 *   파일명에 한글이 들어가는데, 자바는 파일명 인코딩에 file.encoding이 아니라
 *   sun.jnu.encoding(OS 로케일)을 사용한다. 환경에 따라 이름을 조정해야 할 수 있으므로
 *   설정 한 곳만 고치면 되도록 뺐다.
 *
 * ※ 여기서 다루는 것은 파일명 인코딩이다. 파일 내용의 인코딩은 설정하지 않는다.
 *   원본 배포본은 CP949지만 편집기에서 열어 저장하면 UTF-8로 바뀌므로 고정할 수 없다.
 *   리더가 읽는 시점에 판별한다 — BikeRouteCsvReader 클래스 주석 참조.
 *
 * @param bikeRouteCoords     국토종주 자전거길 노선 좌표 CSV
 * @param bikeRouteFacilities 국토종주 자전거길 주변 시설 CSV
 */
@ConfigurationProperties(prefix = "ridely.seed")
public record SeedFileProperties(
        String bikeRouteCoords,
        String bikeRouteFacilities
) {
}
