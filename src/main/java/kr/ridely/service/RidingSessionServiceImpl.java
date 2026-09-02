package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.common.PageResponse;
import kr.ridely.dao.RidingSessionDao;
import kr.ridely.dao.RidingSessionSpatialDao;
import kr.ridely.dto.rideHistory.RidingSessionEndRequestDTO;
import kr.ridely.dto.rideHistory.RidingSessionResponseDTO;
import kr.ridely.dto.rideHistory.RidingSessionStartRequestDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 라이딩 세션 구현.
 */
@Service
@RequiredArgsConstructor
public class RidingSessionServiceImpl implements RidingSessionService {

    private static final Logger log = LoggerFactory.getLogger(RidingSessionServiceImpl.class);

    private final RidingSessionDao ridingSessionDao;
    private final RidingSessionSpatialDao ridingSessionSpatialDao;

    @Override
    @Transactional
    public RidingSessionResponseDTO start(long userId, RidingSessionStartRequestDTO request) {
        // 자유 주행은 본문을 통째로 생략할 수 있어 request가 null로 온다
        Long recommendedRouteId = request == null ? null : request.getRecommendedRouteId();

        long ridingSessionId;
        try {
            ridingSessionId = ridingSessionDao.insertReturningId(userId, recommendedRouteId);
        } catch (DataIntegrityViolationException e) {
            // FK 위반이다. 지워졌거나 없는 추천 코스 번호를 보냈다
            log.warn("없는 추천 코스로 라이딩을 시작하려 했다: userId={} recommendedRouteId={}",
                    userId, recommendedRouteId);
            throw new BusinessException(ErrorCode.COMMON_004);
        }
        return findExisting(userId, ridingSessionId);
    }

    @Override
    @Transactional
    public RidingSessionResponseDTO end(long userId, long ridingSessionId,
                                        RidingSessionEndRequestDTO request) {
        // 존재·소유를 먼저 확인한다. UPDATE가 0행이면 없어서인지, 남의 것이어서인지,
        // 이미 끝나서인지 구분할 수 없다
        RidingSessionResponseDTO current = findExisting(userId, ridingSessionId);

        if (current.getEndedAt() != null) {
            // 재시도로 본다. 모바일에서 종료 요청이 타임아웃돼 앱이 다시 보내는 경우가 흔하고,
            // 그때 에러를 내면 실제로는 저장된 기록을 두고 실패 화면이 뜬다.
            // 값이 달라도 덮어쓰지 않는다 - 첫 요청이 실제 주행에 더 가깝다
            log.info("이미 종료된 세션에 종료 요청이 왔다. 기존 기록을 그대로 돌려준다: "
                    + "ridingSessionId={} userId={}", ridingSessionId, userId);
            return current;
        }

        if (ridingSessionSpatialDao.end(ridingSessionId, userId, request) == 0) {
            // 위에서 존재·소유·진행 중을 모두 확인했는데도 0행이면 그사이 다른 요청이
            // 먼저 종료했다는 뜻이다. 같은 재시도 상황이라 똑같이 현재 기록을 돌려준다
            log.info("종료 처리 중 다른 요청이 먼저 끝냈다: ridingSessionId={} userId={}",
                    ridingSessionId, userId);
            return findExisting(userId, ridingSessionId);
        }

        // TODO(W5): 임베딩 비동기 적재. 종료된 세션을 riding_history_embedding에 넣어
        //           다음 추천의 rider_context로 쓴다. 동기로 하면 종료 응답이 임베딩 API를 기다린다

        return findExisting(userId, ridingSessionId);
    }

    @Override
    public RidingSessionResponseDTO findById(long userId, long ridingSessionId) {
        RidingSessionResponseDTO session = findExisting(userId, ridingSessionId);

        // 트랙만 따로 읽는다. ST_AsGeoJSON이 필요해 MyBatis 조회에 섞지 않았다
        session.setTrackGeoJson(
                ridingSessionSpatialDao.selectTrackGeoJson(ridingSessionId, userId));
        return session;
    }

    @Override
    public PageResponse<RidingSessionResponseDTO> findByUserId(long userId, int page, int size) {
        long total = ridingSessionDao.countByUserId(userId);

        // 기록이 없으면 목록 쿼리를 돌리지 않는다
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        List<RidingSessionResponseDTO> content =
                ridingSessionDao.selectByUserId(userId, page * size, size);
        return PageResponse.of(content, page, size, total);
    }

    /**
     * 세션을 읽고 없으면 이유를 가려 예외를 던진다.
     *
     * user_id를 WHERE에 넣고 조회하므로 "없는 번호"와 "남의 기록"이 똑같이 null로 온다. 둘을 구분하려고 소유자를 보지 않는 존재 확인을 한 번 더 한다. SavedRouteServiceImpl과 같은 방식이다.
     */
    private RidingSessionResponseDTO findExisting(long userId, long ridingSessionId) {
        RidingSessionResponseDTO session = ridingSessionDao.selectById(ridingSessionId, userId);
        if (session != null) {
            return session;
        }
        if (ridingSessionDao.existsById(ridingSessionId)) {
            log.warn("남의 라이딩 기록에 접근했다: userId={} ridingSessionId={}", userId, ridingSessionId);
            throw new BusinessException(ErrorCode.COMMON_003);
        }
        throw new BusinessException(ErrorCode.COMMON_004);
    }
}
