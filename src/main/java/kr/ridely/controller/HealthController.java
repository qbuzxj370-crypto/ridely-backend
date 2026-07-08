package kr.ridely.controller;

import kr.ridely.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 헬스체크 엔드포인트.
 * GET /api/v1/health → ApiResponse 형식으로 상태 반환.
 *
 * Spring Boot Actuator의 /actuator/health와 별개.
 * envelope 통일을 위해 직접 작성한다.
 *
 * SecurityConfig에서 permitAll 대상.
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
    public record HealthStatus(
            String status,
            OffsetDateTime timestamp,
            String version
    ) {}
}
