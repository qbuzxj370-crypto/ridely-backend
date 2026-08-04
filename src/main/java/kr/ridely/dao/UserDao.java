package kr.ridely.dao;

import kr.ridely.dto.user.UserUpdateRequestDTO;
import kr.ridely.vo.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 회원 정보 조회·수정 Mapper (ADR-002: 단순 CRUD → MyBatis).
 * SQL은 resources/mapper/UserMapper.xml.
 *
 * 인증(로그인·토큰)은 AuthDao, 회원 정보 관리는 이 DAO로 나눈다.
 * 두 관심사가 한 파일에 섞이면 쿼리가 늘어날수록 찾기 어려워진다.
 */
@Mapper
public interface UserDao {

    /**
     * 회원 정보 조회.
     *
     * @return 없으면 null (토큰은 유효한데 계정이 삭제된 경우 등)
     */
    AppUser selectById(@Param("userId") long userId);

    /**
     * 회원 정보 부분 수정.
     *
     * 요청에 담긴 항목만 갱신한다(PATCH). null인 필드는 기존 값을 유지한다.
     *
     * @return 갱신 행 수 (변경할 항목이 하나도 없으면 0)
     */
    int updateProfile(@Param("userId") long userId,
                      @Param("request") UserUpdateRequestDTO request);

    /**
     * 회원 탈퇴 (소프트 삭제).
     * 행을 지우지 않고 상태만 바꾼다. 라이딩 기록·저장 경로가 참조하고 있어
     * 실제로 삭제하면 이력이 함께 사라진다.
     *
     * @return 갱신 행 수
     */
    int withdraw(@Param("userId") long userId);
}
