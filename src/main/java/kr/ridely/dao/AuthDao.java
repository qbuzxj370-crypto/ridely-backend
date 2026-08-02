package kr.ridely.dao;

import kr.ridely.vo.AppUser;
import kr.ridely.vo.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 인증 도메인 MyBatis Mapper 인터페이스 (ADR-002: 단순 CRUD -> MyBatis).
 * SQL은 resources/mapper/AuthMapper.xml.
 *
 * MyBatisConfig의 @MapperScan("kr.ridely.dao")이 스캔한다.
 */
@Mapper
public interface AuthDao {

    /**
     * app_user 1행 INSERT.
     * useGeneratedKeys로 INSERT 후 user.userId가 채워진다.
     *
     * @return 삽입 행 수 (성공 시 1)
     */
    int insertUser(AppUser user);

    /**
     * login_id 중복 확인.
     *
     * @return 해당 loginId 보유 행 수 (0 = 미가입, 1 이상 = 중복 -> AUTH-101)
     */
    int existsByLoginId(@Param("loginId") String loginId);

    /**
     * loginId로 회원 1행 조회 (없으면 null).
     * 회원가입 직후 DB 확정값(status·created_at) 회수용.
     * 2주차 로그인(비밀번호 대조)에서도 그대로 재사용한다.
     */
    AppUser selectByLoginId(@Param("loginId") String loginId);

    /**
     * 마지막 로그인 시각 갱신 (로그인 성공 시).
     *
     * @return 갱신 행 수 (1)
     */
    int updateLastLoginAt(@Param("userId") long userId);

    /**
     * 리프레시 토큰 발급 이력 저장.
     *
     * 토큰 원문이 아니라 해시를 저장한다. DB가 유출돼도 그 값으로는 인증할 수 없다.
     *
     * @return 삽입 행 수 (1)
     */
    int insertRefreshToken(RefreshToken refreshToken);

    /**
     * 회원 번호로 회원 1행 조회 (없으면 null).
     * 토큰에서 얻은 userId로 계정 상태를 확인할 때 사용한다.
     */
    AppUser selectByUserId(@Param("userId") long userId);

    /**
     * 리프레시 토큰 해시로 발급 이력 조회.
     *
     * 폐기 여부(revoked_at)·만료 시각(expires_at)까지 함께 가져와
     * 재발급 가능한 토큰인지 서비스에서 판단한다.
     *
     * @return 해당 해시가 없으면 null
     */
    RefreshToken selectRefreshTokenByHash(@Param("tokenHash") String tokenHash);

    /**
     * 리프레시 토큰 폐기 (로그아웃·재발급 시 기존 토큰 무효화).
     * 이미 폐기된 토큰은 시각을 덮어쓰지 않는다.
     *
     * @return 갱신 행 수 (이미 폐기됐거나 없으면 0)
     */
    int revokeRefreshToken(@Param("tokenHash") String tokenHash);

    /**
     * 사용자의 유효한 리프레시 토큰을 모두 폐기.
     * 탈퇴 처리 등 전체 세션을 끊어야 할 때 사용한다.
     *
     * @return 갱신 행 수
     */
    int revokeAllRefreshTokens(@Param("userId") long userId);

    /**
     * user_settings 기본값 1행 INSERT (회원가입 트랜잭션에서 app_user 직후 호출).
     * user_id 외 전 컬럼이 스키마 DEFAULT로 채워진다.
     *
     * @return 삽입 행 수 (성공 시 1)
     */
    int insertDefaultSettings(@Param("userId") long userId);
}