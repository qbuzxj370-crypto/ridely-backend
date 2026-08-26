package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dao.UserSettingsDao;
import kr.ridely.dto.user.UserSettingsDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 회원 설정 조회·수정 구현.
 */
@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsServiceImpl.class);

    /** 우선순위 세 값의 합. 추천 요청 검증(RouteRecommendServiceImpl)과 같은 규칙이다 */
    private static final BigDecimal PRIORITY_SUM = BigDecimal.ONE;

    private final UserSettingsDao userSettingsDao;

    @Override
    public UserSettingsDTO findByUserId(long userId) {
        return getExistingSettings(userId);
    }

    @Override
    @Transactional
    public UserSettingsDTO update(long userId, UserSettingsDTO request) {
        verifyPriorities(request);

        // 존재 확인을 먼저 한다. 없는 회원에 UPDATE를 실행하면 0행이 갱신되는데,
        // 그것만으로는 "설정 행이 없어서"인지 "바꿀 항목이 없어서"인지 구분할 수 없다
        getExistingSettings(userId);

        userSettingsDao.updateSettings(userId, request);

        // 갱신된 전체 설정을 다시 읽어 응답한다. 요청에 없던 항목의 현재 값도 함께 돌려줘야
        // 화면이 다른 항목을 다시 조회하지 않는다
        return getExistingSettings(userId);
    }

    @Override
    public boolean isAvoidRequested(Long userId, Boolean avoidHeader) {
        if (userId == null) {
            return Boolean.TRUE.equals(avoidHeader);
        }

        UserSettingsDTO settings = userSettingsDao.selectById(userId);
        if (settings == null) {
            // 가입 트랜잭션이 기본값 행을 만들므로 정상 회원에게는 없을 수 없다.
            // 없다면 데이터가 어긋난 것이라 조용히 넘기지 않는다
            log.warn("회원 설정 행이 없다: userId={}. 회피를 끈 것으로 본다", userId);
            return false;
        }
        return Boolean.TRUE.equals(settings.getAvoidDangerZones());
    }

    /**
     * 우선순위 묶음 검증.
     *
     * 셋 다 없으면 우선순위를 건드리지 않는 요청이라 통과시킨다. 하나라도 있으면 셋 다 있어야 하고 합이 1이어야 한다.
     *
     * 부분 수정을 허용하지 않는 이유는 합계를 지킬 방법이 없어서다. convenience만 0.6으로 바꾸면 나머지는 기존 값(0.30·0.20)이 남아 합이 1.10이 된다. 나머지를 서버가 비례 조정하는 방법도 있지만, 사용자가 만지지 않은 값을 서버가 바꾸면 화면의 슬라이더와 저장값이 어긋난다.
     *
     * 오차를 허용하지 않는다. 추천 요청 검증과 같은 규칙이라야 같은 값이 한쪽만 통과하는 일이 없다. 슬라이더 셋을 다루는 화면은 마지막 값을 1 - a - b로 계산해 보내면 된다.
     *
     * DB CHECK 제약은 각 값이 0~1인지만 보고 합은 보지 않는다.
     */
    private void verifyPriorities(UserSettingsDTO request) {
        BigDecimal convenience = request.getDefaultPriorityConvenience();
        BigDecimal exercise = request.getDefaultPriorityExercise();
        BigDecimal scenery = request.getDefaultPriorityScenery();

        boolean noneGiven = convenience == null && exercise == null && scenery == null;
        if (noneGiven) {
            return;
        }

        boolean allGiven = convenience != null && exercise != null && scenery != null;
        if (!allGiven) {
            log.warn("우선순위를 일부만 보냈다: convenience={} exercise={} scenery={}",
                    convenience, exercise, scenery);
            throw new BusinessException(ErrorCode.ROUTE_001);
        }

        // compareTo로 비교한다. equals는 소수 자릿수까지 보므로 0.5와 0.50을 다르게 판정한다
        BigDecimal sum = convenience.add(exercise).add(scenery);
        if (sum.compareTo(PRIORITY_SUM) != 0) {
            log.warn("우선순위 합이 1이 아니다: {} (convenience={} exercise={} scenery={})",
                    sum, convenience, exercise, scenery);
            throw new BusinessException(ErrorCode.ROUTE_001);
        }
    }

    /**
     * 설정 조회. 없으면 COMMON-004.
     *
     * 가입 트랜잭션이 기본값 행을 만들므로 정상 회원에게는 없을 수 없다. 그래도 방어하는 이유는 그 트랜잭션 이전에 만들어진 계정이나 데이터 손상이 있을 수 있어서다. 그대로 두면 null 응답이 나가고 화면이 토글을 잘못 그린다.
     */
    private UserSettingsDTO getExistingSettings(long userId) {
        UserSettingsDTO settings = userSettingsDao.selectById(userId);
        if (settings == null) {
            log.warn("회원 설정 행이 없다: userId={}", userId);
            throw new BusinessException(ErrorCode.COMMON_004);
        }
        return settings;
    }
}
