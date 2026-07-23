package kr.ridely.dao;

import kr.ridely.vo.AppUser;
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
     * user_settings 기본값 1행 INSERT (회원가입 트랜잭션에서 app_user 직후 호출).
     * user_id 외 전 컬럼이 스키마 DEFAULT로 채워진다.
     *
     * @return 삽입 행 수 (성공 시 1)
     */
    int insertDefaultSettings(@Param("userId") long userId);
}