package kr.ridely.dao;

import kr.ridely.dto.savedRoute.SavedRouteCreateRequestDTO;
import kr.ridely.dto.savedRoute.SavedRouteResponseDTO;
import kr.ridely.dto.savedRoute.SavedRouteUpdateRequestDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 저장 경로 MyBatis Mapper 인터페이스 (ADR-002: 공간 연산이 없는 CRUD -> MyBatis).
 * SQL은 resources/mapper/SavedRouteMapper.xml.
 *
 * 경로 형상(route_geom)은 여기서 다루지 않는다. 저장 경로는 recommended_route를 가리키기만 하고 좌표는 GET /routes/{id}가 준다.
 *
 * <b>소유권 검증은 서비스가 아니라 SQL에서 한다.</b> 모든 단건 메서드가 WHERE에 user_id를 함께 받는다. 조회 후 자바에서 비교하면 검증을 빠뜨린 메서드가 하나만 있어도 남의 기록이 나간다.
 */
@Mapper
public interface SavedRouteDao {

    /**
     * 저장하고 생성된 번호를 돌려받는다.
     *
     * 매퍼에서 INSERT ... RETURNING을 쓴다. MyBatis의 insert는 반환값이 갱신 행 수뿐이라 생성 키를 받으려면 요청 DTO에 savedRouteId 필드를 만들어야 하는데, 클라이언트가 보내지 않는 값을 요청 DTO에 둘 이유가 없다.
     *
     * @throws org.springframework.dao.DuplicateKeyException 같은 코스를 이미 저장한 경우 (UNIQUE(user_id, recommended_route_id))
     * @throws org.springframework.dao.DataIntegrityViolationException 없는 추천 코스를 가리킨 경우 (FK)
     * @return 생성된 saved_route_id
     */
    long insertReturningId(@Param("userId") long userId,
                           @Param("request") SavedRouteCreateRequestDTO request);

    /**
     * 단건을 읽는다. recommended_route를 조인해 요약 정보를 함께 담는다.
     *
     * @return 저장 경로. 없거나 남의 것이면 null - 호출부가 둘을 구분해야 하면 existsById를 함께 쓴다
     */
    SavedRouteResponseDTO selectById(@Param("savedRouteId") long savedRouteId,
                                     @Param("userId") long userId);

    /**
     * 목록을 읽는다.
     *
     * @param favoriteOnly true면 즐겨찾기만. null·false면 전체
     * @param sort         latest(저장 최신순) 또는 name(이름 오름차순). 그 밖의 값은 매퍼가 latest로 떨어뜨린다
     */
    List<SavedRouteResponseDTO> selectByUserId(@Param("userId") long userId,
                                               @Param("favoriteOnly") Boolean favoriteOnly,
                                               @Param("sort") String sort,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /** 목록의 전체 건수. 페이지 응답의 totalElements·totalPages 계산에 쓴다 */
    long countByUserId(@Param("userId") long userId,
                       @Param("favoriteOnly") Boolean favoriteOnly);

    /**
     * 이름·메모·즐겨찾기를 부분 수정한다. null인 필드는 SET 절에서 빠진다.
     *
     * @return 갱신된 행 수. 0이면 없거나 남의 것이거나 바꿀 항목이 없었다는 뜻이라 호출부가 존재 확인을 먼저 해야 한다
     */
    int update(@Param("savedRouteId") long savedRouteId,
               @Param("userId") long userId,
               @Param("request") SavedRouteUpdateRequestDTO request);

    /**
     * 삭제한다. 추천 코스 원본(recommended_route)은 남는다.
     *
     * @return 삭제된 행 수. 0이면 없거나 남의 것이다
     */
    int delete(@Param("savedRouteId") long savedRouteId, @Param("userId") long userId);

    /**
     * 소유자를 가리지 않고 존재만 확인한다.
     *
     * 404와 403을 구분하려고 둔다. selectById가 null을 주는 경우가 "없다"와 "남의 것이다" 둘이라 그것만으로는 판정할 수 없다.
     */
    boolean existsById(@Param("savedRouteId") long savedRouteId);
}
