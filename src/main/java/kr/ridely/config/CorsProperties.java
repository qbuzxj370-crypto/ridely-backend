package kr.ridely.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 설정값 바인딩.
 * application.yml의 ridely.cors.* 를 주입받는다.
 *
 * ※ @Value("${...}")는 YAML 리스트(- 항목)를 읽지 못한다.
 *   (Spring이 리스트를 [0], [1] 개별 키로 평탄화하기 때문)
 *   리스트는 반드시 이 방식(@ConfigurationProperties)으로 받는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ridely.cors")
public class CorsProperties {

    /** 허용할 출처 목록 (프로파일별 오버라이드) */
    private List<String> allowedOrigins;

    /** 허용할 HTTP 메서드 목록 */
    private List<String> allowedMethods;
}