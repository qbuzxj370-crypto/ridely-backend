package kr.ridely.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 우리 API 문서(Swagger UI) 설정.
 *
 * 접속 주소: http://localhost:8080/swagger-ui.html
 *
 * 인증이 필요한 API는 문서 화면 우측 상단 [Authorize] 버튼에 액세스 토큰을 넣으면
 * 이후 요청에 자동으로 첨부된다. 토큰은 POST /auth/login 응답에서 얻는다.
 *
 * ※ 코드에 등장하는 OpenAPI 타입은 "API 명세를 기술하는 표준 규격"의 이름이다.
 *   공공데이터포털에서 말하는 OpenAPI(공개 API 서비스, 예: TourAPI)와는 다른 의미이며
 *   그쪽 설정은 infra/tourapi 패키지에 있다.
 */
@Configuration
public class ApiDocsConfig {

    /** Authorize 버튼에 표시될 인증 방식 이름 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI ridelyApiDocs() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, jwtScheme()))
                // 전역 기본값으로 인증을 걸어 두고, 공개 API는 각 컨트롤러에서 해제한다.
                // 대부분의 API가 인증을 요구하므로 이쪽이 표기가 적고 누락 위험도 낮다.
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    private Info apiInfo() {
        return new Info()
                .title("Ridely API")
                .version("v1")
                .description("""
                        자전거 라이딩 코스 추천 서비스 Ridely의 백엔드 API.

                        ## 응답 형식
                        모든 응답은 아래 형태로 감싸여 있다.
                        ```json
                        { "success": true,  "data": { ... }, "error": null }
                        { "success": false, "data": null,    "error": { "code": "AUTH-201", "message": "...", "details": null } }
                        ```
                        성공 여부는 HTTP 상태 코드와 `success` 필드 모두로 판단할 수 있다.
                        입력값 검증에 실패하면 `error.details`에 어느 항목이 왜 틀렸는지 담긴다.

                        ## 인증
                        1. `POST /auth/login`으로 액세스 토큰(1시간)과 리프레시 토큰(14일)을 받는다.
                        2. 이후 요청 헤더에 `Authorization: Bearer {accessToken}`을 넣는다.
                        3. `AUTH-301`(만료) 응답을 받으면 `POST /auth/refresh`로 갱신 후 원 요청을 재시도한다.
                        4. `AUTH-302`(재로그인 필요) 또는 `COMMON-002`(로그인 필요) 응답을 받으면
                           저장된 토큰을 지우고 로그인 화면으로 이동한다.

                        재발급에 사용한 리프레시 토큰은 즉시 폐기되므로,
                        응답으로 받은 새 토큰으로 반드시 교체해야 한다.
                        """)
                .contact(new Contact().name("Ridely 백엔드"));
    }

    /**
     * 호출 대상 서버 목록.
     * 문서 화면에서 선택할 수 있으며, 안드로이드 에뮬레이터는 호스트를 10.0.2.2로 본다.
     */
    private List<Server> servers() {
        return List.of(
                new Server().url("http://localhost:8080").description("로컬 개발"),
                new Server().url("http://10.0.2.2:8080").description("안드로이드 에뮬레이터")
        );
    }

    /** Authorization: Bearer {토큰} 형식임을 문서에 알린다 */
    private SecurityScheme jwtScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 응답의 accessToken을 입력한다. 'Bearer ' 접두사는 자동으로 붙는다.");
    }
}
