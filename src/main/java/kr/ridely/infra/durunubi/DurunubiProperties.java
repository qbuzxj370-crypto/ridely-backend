package kr.ridely.infra.durunubi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국관광공사 두루누비 정보 서비스 설정 바인딩.
 * application.yml의 ridely.external.durunubi.* 값을 주입받는다.
 *
 * ★ 스파이크 전용 설정이다. 두루누비 채택이 기각되면 이 파일도 함께 삭제한다.
 *
 * <p>서비스키: data.go.kr은 계정당 하나의 인증키를 발급하고 승인된 모든 서비스에 공용으로 쓴다.
 * 따라서 기본값으로 TOUR_API_KEY(KorService2용)를 그대로 물려받되,
 * 별도 키를 쓰는 상황을 대비해 DURUNUBI_API_KEY로 덮어쓸 수 있게 뒀다.
 *
 * @param baseUrl        https://apis.data.go.kr/B551011/Durunubi
 * @param serviceKey     data.go.kr 발급 서비스 키
 * @param timeoutSeconds API 호출 타임아웃
 * @param gpxTimeoutSeconds GPX 파일 다운로드 타임아웃 (본문이 커서 API보다 넉넉하게)
 * @param maxGpxDownloads 스파이크 1회 실행당 GPX 다운로드 상한 (durunubi.kr 부하 방지)
 */
@ConfigurationProperties(prefix = "ridely.external.durunubi")
public record DurunubiProperties(
        String baseUrl,
        String serviceKey,
        int timeoutSeconds,
        int gpxTimeoutSeconds,
        int maxGpxDownloads
) {
}
