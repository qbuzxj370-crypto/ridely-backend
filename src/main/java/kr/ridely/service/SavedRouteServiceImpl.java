package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.common.PageResponse;
import kr.ridely.dao.SavedRouteDao;
import kr.ridely.dto.savedRoute.SavedRouteCreateRequestDTO;
import kr.ridely.dto.savedRoute.SavedRouteResponseDTO;
import kr.ridely.dto.savedRoute.SavedRouteUpdateRequestDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 저장 경로 구현.
 */
@Service
@RequiredArgsConstructor
public class SavedRouteServiceImpl implements SavedRouteService {

    private static final Logger log = LoggerFactory.getLogger(SavedRouteServiceImpl.class);

    private final SavedRouteDao savedRouteDao;

    @Override
    @Transactional
    public SavedRouteResponseDTO save(long userId, SavedRouteCreateRequestDTO request) {
        long savedRouteId;
        try {
            savedRouteId = savedRouteDao.insertReturningId(userId, request);
        } catch (DuplicateKeyException e) {
            // UNIQUE(user_id, recommended_route_id) 위반이다
            log.info("이미 저장한 코스다: userId={} recommendedRouteId={}",
                    userId, request.getRecommendedRouteId());
            throw new BusinessException(ErrorCode.SAVED_001);
        } catch (DataIntegrityViolationException e) {
            // FK 위반이다. DuplicateKeyException이 이 예외의 하위라 잡는 순서가 중요하다
            log.warn("없는 추천 코스를 저장하려 했다: userId={} recommendedRouteId={}",
                    userId, request.getRecommendedRouteId());
            throw new BusinessException(ErrorCode.COMMON_004);
        }

        // 조인해서 요약 정보(제목·거리·강도)까지 채운 형태로 돌려준다.
        // 저장 직후 화면이 목록으로 돌아가면서 방금 담은 항목을 그려야 하는데,
        // 번호만 주면 화면이 곧바로 목록을 다시 부르게 된다
        return findById(userId, savedRouteId);
    }

    @Override
    public SavedRouteResponseDTO findById(long userId, long savedRouteId) {
        SavedRouteResponseDTO saved = savedRouteDao.selectById(savedRouteId, userId);
        if (saved == null) {
            throw notFoundOrForbidden(userId, savedRouteId);
        }
        return saved;
    }

    @Override
    public PageResponse<SavedRouteResponseDTO> findByUserId(long userId, Boolean favoriteOnly,
                                                            String sort, int page, int size) {
        long total = savedRouteDao.countByUserId(userId, favoriteOnly);

        // 건수가 0이면 목록 쿼리를 돌리지 않는다. 저장한 코스가 하나도 없는 신규 회원이
        // 목록 화면을 열 때마다 조인 쿼리가 헛돈다
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        List<SavedRouteResponseDTO> content =
                savedRouteDao.selectByUserId(userId, favoriteOnly, sort, page * size, size);
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public SavedRouteResponseDTO update(long userId, long savedRouteId,
                                        SavedRouteUpdateRequestDTO request) {
        // 존재·소유 확인을 먼저 한다. UPDATE가 0행을 갱신했다는 것만으로는
        // "없어서"인지 "남의 것이라서"인지 "바꿀 항목이 없어서"인지 구분할 수 없다
        SavedRouteResponseDTO current = findById(userId, savedRouteId);

        // 빈 본문이면 UPDATE를 부르지 않는다. 세 필드가 모두 null이면 매퍼의 <set>이
        // 비어 UPDATE saved_route WHERE ... 가 되고 SQL 문법 오류로 500이 난다.
        // user_settings는 updated_at = NOW()가 항상 붙어 이 문제가 없지만
        // saved_route에는 그 컬럼이 없다
        if (request.getCustomName() == null && request.getMemo() == null
                && request.getIsFavorite() == null) {
            return current;
        }

        savedRouteDao.update(savedRouteId, userId, request);

        // 갱신된 전체를 다시 읽어 응답한다. 보내지 않은 항목의 현재 값도 함께 돌려줘야
        // 화면이 목록을 다시 조회하지 않는다
        return findById(userId, savedRouteId);
    }

    @Override
    @Transactional
    public void delete(long userId, long savedRouteId) {
        if (savedRouteDao.delete(savedRouteId, userId) == 0) {
            throw notFoundOrForbidden(userId, savedRouteId);
        }
    }

    /**
     * 대상이 잡히지 않은 이유를 가려 예외를 만든다.
     *
     * user_id를 WHERE에 넣고 조회하므로 "없는 번호"와 "남의 기록"이 똑같이 0행으로 온다. 둘을 구분하려고 소유자를 보지 않는 존재 확인을 한 번 더 한다.
     *
     * 남의 기록에 404를 주면 번호를 훑어 남의 저장 목록 크기를 알아낼 수 없다는 장점이 있지만, 저장 경로 번호가 그 정도로 민감하지 않고 403이 화면에 더 정확한 안내를 준다. 기획의 소유권 검증 항목도 COMMON-003을 명시한다.
     */
    private BusinessException notFoundOrForbidden(long userId, long savedRouteId) {
        if (savedRouteDao.existsById(savedRouteId)) {
            log.warn("남의 저장 경로에 접근했다: userId={} savedRouteId={}", userId, savedRouteId);
            return new BusinessException(ErrorCode.COMMON_003);
        }
        return new BusinessException(ErrorCode.COMMON_004);
    }
}
