package kr.ridely;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ridely 애플리케이션 진입점.
 *
 * - @ConfigurationPropertiesScan: kr.ridely 하위의 @ConfigurationProperties
 *   (TourApiProperties 등)를 자동 스캔·등록
 * - @EnableScheduling: 공공데이터 배치 스케줄러용 (Spring Scheduler)
 *
 * MyBatis @MapperScan은 config/MyBatisConfig에 분리.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RidelyApplication {

    public static void main(String[] args) {
        SpringApplication.run(RidelyApplication.class, args);
    }
}
