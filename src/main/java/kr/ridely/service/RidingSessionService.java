package kr.ridely.service;

import kr.ridely.common.PageResponse;
import kr.ridely.dto.rideHistory.RidingSessionEndRequestDTO;
import kr.ridely.dto.rideHistory.RidingSessionResponseDTO;
import kr.ridely.dto.rideHistory.RidingSessionStartRequestDTO;

/**
 * 라이딩 세션 서비스.
 *
 * 라이딩을 시작해 진행 중으로 두고, 끝날 때 측정값과 GPS 트랙을 한 번에 받아 마무리한다. 이 기록이 쌓여 W5의 개인화 추천 재료가 된다.
 *
 * <b>모든 메서드가 userId를 첫 인자로 받는다.</b> 컨트롤러가 토큰에서 꺼낸 값이라 클라이언트가 바꿀 수 없다.
 */
public interface RidingSessionService {

    /**
     * 라이딩을 시작한다. 시작 시각은 서버가 찍는다.
     *
     * request가 null일 수 있다. 자유 주행이면 보낼 값이 없어 본문을 통째로 생략하는 것이 자연스럽고, 컨트롤러가 그 요청을 받아 준다.
     *
     * 진행 중인 세션이 이미 있어도 막지 않는다. 앱이 죽었다 살아나면 이전 세션이 진행 중으로 남아 있는데, 새 라이딩을 시작하지 못하면 사용자가 앱을 쓸 수 없게 된다. 남은 세션은 목록에서 종료 시각이 비어 있는 것으로 확인한다.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (없는 추천 코스)
     */
    RidingSessionResponseDTO start(long userId, RidingSessionStartRequestDTO request);

    /**
     * 라이딩을 종료한다. 종료 시각은 서버가 찍는다.
     *
     * 이미 끝난 세션에 다시 요청하면 기록을 덮어쓰지 않고 현재 기록을 그대로 돌려준다. 모바일에서 종료 요청이 타임아웃돼 앱이 재시도하는 경우가 흔한데, 그때 에러를 내면 실제로는 저장된 기록을 두고 실패 화면이 뜬다.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (없음), COMMON-003 (남의 것), COMMON-001 (GPS 트랙 형식 오류)
     */
    RidingSessionResponseDTO end(long userId, long ridingSessionId, RidingSessionEndRequestDTO request);

    /**
     * 라이딩 기록 하나를 읽는다. <b>GPS 트랙이 여기서만 채워진다.</b>
     *
     * 기록 상세 화면이 지난 주행 궤적을 지도에 그리는 데 쓴다.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (없음), COMMON-003 (남의 것)
     */
    RidingSessionResponseDTO findById(long userId, long ridingSessionId);

    /**
     * 내 라이딩 기록 목록. 최근 시작한 순이다.
     *
     * 응답에 GPS 트랙은 담기지 않는다. 트랙 하나가 좌표 수백~수천 개라 한 페이지가 수 MB가 된다. 궤적이 필요하면 단건 조회를 쓴다.
     */
    PageResponse<RidingSessionResponseDTO> findByUserId(long userId, int page, int size);
}
