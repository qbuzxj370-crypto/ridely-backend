package kr.ridely.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dto.rideHistory.RidingSessionEndRequestDTO;
import kr.ridely.dto.rideHistory.RidingSessionResponseDTO;
import kr.ridely.dto.rideHistory.RidingSessionStartRequestDTO;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 라이딩 종료 통합 테스트.
 *
 * <b>이 클래스의 존재 이유는 첫 번째 테스트다.</b> 종료 UPDATE는 JdbcClient가 실행하고 응답용 재조회는 MyBatis가 한다. MyBatis의 1차 캐시는 자기 UPDATE가 나갈 때만 비워지므로, 캐시 범위가 SESSION이면 같은 트랜잭션 안의 재조회가 갱신 전 객체를 그대로 돌려준다. 실제로 종료 응답이 종료 전 값으로 나갔고 application.yml의 local-cache-scope를 STATEMENT로 두어 막았다.
 *
 * 설정 한 줄이라 지우기 쉽고 지워도 컴파일은 통과한다. 그래서 테스트로 고정한다.
 *
 * 나머지 두 테스트는 이 PR의 판단 두 가지를 고정한다 - 재종료 멱등과 트랙 노출 범위다.
 */
class RidingSessionEndTest extends AbstractIntegrationTest {

    private static final BigDecimal 주행_거리 = new BigDecimal("14.60");
    private static final BigDecimal 평균_속도 = new BigDecimal("16.2");

    /**
     * 한강 선유도 부근 3점. 짧지만 LineString 조건(2점 이상)을 만족한다.
     *
     * <b>좌표에 고도를 넣어 둔다.</b> 앱이 고도를 담아 보내면 3차원이 되는데 track_geom 컬럼은 2D라 ST_Force2D로 떨궈야 한다. 2차원 트랙을 보내면 그 처리가 동작하는지 알 수 없다.
     */
    private static final String 트랙 = """
            {"type":"LineString","coordinates":\
            [[126.8997,37.5434,10.5],[126.9050,37.5440,12.0],[126.9100,37.5450,11.2]]}""";

    @Autowired
    private RidingSessionService ridingSessionService;

    @Autowired
    private JdbcClient jdbcClient;

    private long 회원_번호;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE riding_session, app_user RESTART IDENTITY CASCADE").update();

        회원_번호 = jdbcClient.sql("""
                        INSERT INTO app_user (login_id, password_hash, nickname)
                        VALUES ('ride_test', 'dummy-hash', '라이딩테스트')
                        RETURNING user_id
                        """)
                .query(Long.class)
                .single();
    }

    private long 라이딩_시작() {
        // 자유 주행으로 시작한다. 추천 코스를 만들려면 LLM·ORS를 호출해야 하는데
        // 이 테스트가 보려는 것과 무관하다
        return ridingSessionService.start(회원_번호, new RidingSessionStartRequestDTO(null))
                .getRidingSessionId();
    }

    private RidingSessionEndRequestDTO 종료_요청(BigDecimal 거리, boolean 완주, String 트랙값) {
        RidingSessionEndRequestDTO request = new RidingSessionEndRequestDTO();
        request.setDistanceKm(거리);
        request.setAvgSpeedKmh(평균_속도);
        request.setIsCompleted(완주);
        request.setVisitedPoiCount(2);
        request.setAlertReceivedCount(1);
        request.setTrackGeoJson(트랙값);
        return request;
    }

    @Test
    @DisplayName("종료 응답에 갱신된 값이 담긴다 (MyBatis 1차 캐시 회귀)")
    void 종료_응답이_갱신된_값을_담는다() {
        long 세션 = 라이딩_시작();

        RidingSessionResponseDTO 응답 =
                ridingSessionService.end(회원_번호, 세션, 종료_요청(주행_거리, true, 트랙));

        // 아래 넷이 전부 "종료 전 값"으로 나오던 것이 이 테스트가 막는 회귀다.
        // local-cache-scope가 SESSION이면 endedAt은 null, distanceKm도 null,
        // isCompleted는 false, visitedPoiCount는 0이 된다
        assertThat(응답.getEndedAt()).isNotNull();
        assertThat(응답.getDistanceKm()).isEqualByComparingTo(주행_거리);
        assertThat(응답.getIsCompleted()).isTrue();
        assertThat(응답.getVisitedPoiCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 끝난 세션을 다시 종료해도 첫 기록이 남는다")
    void 재종료가_첫_기록을_덮지_않는다() {
        long 세션 = 라이딩_시작();
        ridingSessionService.end(회원_번호, 세션, 종료_요청(주행_거리, true, 트랙));

        // 모바일에서 종료 요청이 타임아웃돼 앱이 재시도하는 상황이다.
        // 에러를 내면 실제로는 저장된 기록을 두고 실패 화면이 뜬다
        RidingSessionResponseDTO 재종료 =
                ridingSessionService.end(회원_번호, 세션, 종료_요청(new BigDecimal("99.90"), false, null));

        assertThat(재종료.getDistanceKm()).isEqualByComparingTo(주행_거리);
        assertThat(재종료.getIsCompleted()).isTrue();
    }

    @Test
    @DisplayName("GPS 트랙은 단건 조회에만 담긴다")
    void 트랙은_단건에서만_나온다() {
        long 세션 = 라이딩_시작();
        RidingSessionResponseDTO 종료_응답 =
                ridingSessionService.end(회원_번호, 세션, 종료_요청(주행_거리, true, 트랙));

        assertThat(종료_응답.getTrackGeoJson()).isNull();
        assertThat(ridingSessionService.findByUserId(회원_번호, 0, 20)
                .getContent().getFirst().getTrackGeoJson()).isNull();

        String 저장된_트랙 = ridingSessionService.findById(회원_번호, 세션).getTrackGeoJson();
        assertThat(저장된_트랙).isNotNull().contains("LineString");

        // 고도를 넣어 보냈지만 ST_Force2D가 Z를 떨궈 좌표가 두 값으로 돌아온다
        assertThat(첫_좌표_차원(저장된_트랙)).isEqualTo(2);
    }

    /** GeoJSON LineString의 첫 좌표가 몇 개 값으로 이뤄졌는지. 2면 2차원, 3이면 고도가 남은 것이다 */
    private int 첫_좌표_차원(String geoJson) {
        try {
            return new ObjectMapper().readTree(geoJson).get("coordinates").get(0).size();
        } catch (JsonProcessingException e) {
            throw new AssertionError("트랙이 GeoJSON이 아니다: " + geoJson, e);
        }
    }

    @Test
    @DisplayName("남의 기록은 종료할 수 없다")
    void 남의_기록은_종료할_수_없다() {
        long 세션 = 라이딩_시작();
        long 다른_회원 = jdbcClient.sql("""
                        INSERT INTO app_user (login_id, password_hash, nickname)
                        VALUES ('other_test', 'dummy-hash', '다른회원')
                        RETURNING user_id
                        """)
                .query(Long.class)
                .single();

        // 메시지가 아니라 에러코드로 단언한다. BusinessException의 메시지는 사용자 문구라
        // 문구를 다듬으면 테스트가 깨진다
        assertThatThrownBy(() -> ridingSessionService.end(다른_회원, 세션, 종료_요청(주행_거리, true, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_003));
    }
}
