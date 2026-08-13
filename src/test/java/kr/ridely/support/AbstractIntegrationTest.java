package kr.ridely.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 공통 베이스.
 *
 * PostGIS·pgvector가 포함된 빈 PostgreSQL 컨테이너를 띄우고,
 * 스키마는 Flyway가 기동 시 적용한다(src/main/resources/db/migration).
 *
 * 테스트가 개발·운영과 같은 마이그레이션 경로를 타는 것이 핵심이다.
 * 이전에는 테스트만 db/schema.sql로 컨테이너를 초기화하고 개발 DB는 손으로 고쳐서,
 * 스키마가 어긋나도 테스트는 통과하고 개발만 깨졌다. 실제로 두 번 그랬다.
 *
 * 컨테이너는 static이라 JVM 당 한 번만 기동되고 모든 테스트가 공유한다.
 * (테스트마다 띄우면 수십 초씩 소요)
 */
@SpringBootTest(properties = {
        // Spring AI는 기동 시 API 키를 요구한다. 이 테스트는 LLM을 호출하지 않으므로 컨텍스트 로딩만 통과하도록 더미 값을 넣는다.
        "spring.ai.google.genai.api-key=test-dummy-key",
        // 운영 설정에는 기본값이 없다(공개 저장소라 기본값을 두지 않는다).
        // 테스트는 환경변수에 의존하지 않도록 여기서 값을 주입한다. 32바이트 이상이어야 한다.
        "ridely.jwt.secret=ridely-integration-test-secret-key-32bytes-over"
})
// local 프로파일(application-local.yml)은 각자 PC에만 있는 파일이라
// 테스트가 이를 참조하면 다른 환경에서 재현되지 않는다. test 프로파일로 고정한다.
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** docker-compose.yml과 동일한 이미지 (PostGIS + pgvector 번들) */
    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("imresamu/postgis:16-3.5-bundle0-bookworm")
                    .asCompatibleSubstituteFor("postgres");

    @SuppressWarnings("resource") // 컨테이너는 JVM 종료 시 Ryuk이 정리한다
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGIS_IMAGE)
                    .withDatabaseName("ridely_test")
                    .withUsername("ridely")
                    .withPassword("ridely");
    // 스키마는 넣지 않는다. Flyway가 애플리케이션 기동 시 적용한다.

    static {
        POSTGRES.start();
    }

    /** 컨테이너가 무작위 포트로 뜨므로 접속 정보를 실행 시점에 주입한다 */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
