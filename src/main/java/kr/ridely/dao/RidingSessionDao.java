package kr.ridely.dao;

import kr.ridely.dto.rideHistory.RidingSessionResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 라이딩 세션 MyBatis Mapper 인터페이스 (ADR-002: 공간 연산이 없는 CRUD -> MyBatis).
 * SQL은 resources/mapper/RidingSessionMapper.xml.
 *
 * <b>종료 UPDATE는 여기 없다.</b> GPS 트랙을 track_geom에 넣을 때 PostGIS 함수가 필요해 RidingSessionSpatialDao(JdbcClient)로 나눠 뒀다. AccidentZoneDao와 AccidentZoneSpatialDao를 나눈 것과 같은 이유다.
 *
 * SavedRouteDao와 마찬가지로 소유권 검증을 SQL WHERE에서 한다.
 */
@Mapper
public interface RidingSessionDao {

    /**
     * 라이딩을 시작하고 생성된 번호를 돌려받는다. started_at은 서버가 NOW()로 찍는다.
     *
     * @param recommendedRouteId 따라 달릴 추천 코스 번호. 자유 주행이면 null
     * @throws org.springframework.dao.DataIntegrityViolationException 없는 추천 코스를 가리킨 경우 (FK)
     * @return 생성된 riding_session_id
     */
    long insertReturningId(@Param("userId") long userId,
                           @Param("recommendedRouteId") Long recommendedRouteId);

    /**
     * 단건을 읽는다. trackGeoJson은 담기지 않는다.
     *
     * @return 세션. 없거나 남의 것이면 null
     */
    RidingSessionResponseDTO selectById(@Param("ridingSessionId") long ridingSessionId,
                                        @Param("userId") long userId);

    /**
     * 내 라이딩 기록 목록. 최근 시작한 순이다.
     *
     * 진행 중인 세션(ended_at IS NULL)도 함께 나온다. 앱이 죽었다 살아났을 때 이어 달릴 세션을 찾으려면 목록에 있어야 한다.
     */
    List<RidingSessionResponseDTO> selectByUserId(@Param("userId") long userId,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    /** 목록의 전체 건수 */
    long countByUserId(@Param("userId") long userId);

    /**
     * 소유자를 가리지 않고 존재만 확인한다. 404와 403을 구분하는 용도다.
     */
    boolean existsById(@Param("ridingSessionId") long ridingSessionId);
}
