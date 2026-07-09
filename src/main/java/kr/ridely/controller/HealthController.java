package kr.ridely.controller;

import kr.ridely.common.ApiResponse;
import lombok.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 헬스체크 엔드포인트.
 * GET /api/v1/health → ApiResponse 형식으로 상태 반환.
 *
 * Spring Boot Actuator의 /actuator/health와 별개.
 * 우리 API는 전부 ApiResponse 형식으로 감싸는데 Actuator는 그렇지 않기 때문.
 * envelope 통일을 위해 직접 작성한다.
 *
 * SecurityConfig에서 permitAll 대상.(인증 없이 접근 가능)
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        // TODO: 필요 시 DB 연결 상태 등 추가 점검
        HealthStatus status = new HealthStatus("UP", OffsetDateTime.now(), "0.0.1");
        return ApiResponse.ok(status);
    }

    /** 헬스 상태 응답 본문 */
    @Getter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthStatus {
        /** 서버 상태. "UP" 또는 "DOWN" */
        private String status;
        /** 응답 생성 시각 */
        private OffsetDateTime timestamp;
        /** 애플리케이션 버전 */
        private String version;
    }
}
