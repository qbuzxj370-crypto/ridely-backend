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
     * 비밀번호 변경. 현재 비밀번호 확인과 정책 검사는 서비스가 한다.
     *
     * @param passwordHash 새 비밀번호의 BCrypt 해시
     * @return 갱신 행 수 (1)
     */
    int updatePassword(@Param("userId") long userId,
                       @Param("passwordHash") String passwordHash);

    /**
     * 회원 탈퇴 (하드 삭제).
     *
     * 행을 지운다. 개인 기록(설정·저장 경로·라이딩 세션·토큰)은 FK CASCADE로 함께 사라지고, 추천 코스는 {@code SET NULL}로 남는다. 코스는 인증 없이 조회되는 공용 자산이라서다.
     *
     * 소프트 삭제였던 것을 바꿨다. 남기려던 이력이 GPS 궤적이라 탈퇴 뒤 보관할 법적 근거가 없다.
     *
     * @return 삭제 행 수 (1)
     */
    int withdraw(@Param("userId") long userId);
}
