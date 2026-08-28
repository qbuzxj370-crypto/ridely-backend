package kr.ridely.service;

import kr.ridely.common.PageResponse;
import kr.ridely.dto.savedRoute.SavedRouteCreateRequestDTO;
import kr.ridely.dto.savedRoute.SavedRouteResponseDTO;
import kr.ridely.dto.savedRoute.SavedRouteUpdateRequestDTO;

/**
 * 저장 경로 서비스.
 *
 * 회원이 추천받은 코스를 자기 목록에 담고, 이름·메모를 붙이고, 즐겨찾기하고, 지우는 기능이다. 코스 내용 자체는 recommended_route에 있고 여기는 연결과 개인 메모만 다룬다.
 *
 * <b>모든 메서드가 userId를 첫 인자로 받는다.</b> 컨트롤러가 토큰에서 꺼낸 값이라 클라이언트가 바꿀 수 없다. 남의 기록에 손대면 COMMON-003이다.
 */
public interface SavedRouteService {

    /**
     * 추천 코스를 내 목록에 저장한다.
     *
     * @throws kr.ridely.common.BusinessException SAVED-001 (이미 저장한 코스), COMMON-004 (없는 추천 코스)
     */
    SavedRouteResponseDTO save(long userId, SavedRouteCreateRequestDTO request);

    /**
     * 저장한 코스 하나를 읽는다.
     *
     * 경로 좌표는 담기지 않는다. 지도를 그리려면 응답의 recommendedRouteId로 GET /routes/{id}를 부른다.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (없음), COMMON-003 (남의 것)
     */
    SavedRouteResponseDTO findById(long userId, long savedRouteId);

    /**
     * 내 저장 목록을 읽는다.
     *
     * @param favoriteOnly true면 즐겨찾기만. null이면 전체
     * @param sort         latest(기본) 또는 name. 그 밖의 값은 latest로 처리한다
     */
    PageResponse<SavedRouteResponseDTO> findByUserId(long userId, Boolean favoriteOnly,
                                                     String sort, int page, int size);

    /**
     * 이름·메모·즐겨찾기를 수정한다. 보낸 항목만 바뀐다.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (없음), COMMON-003 (남의 것)
     */
    SavedRouteResponseDTO update(long userId, long savedRouteId, SavedRouteUpdateRequestDTO request);

    /**
     * 저장을 취소한다. 추천 코스 원본은 지워지지 않는다.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (없음), COMMON-003 (남의 것)
     */
    void delete(long userId, long savedRouteId);
}
