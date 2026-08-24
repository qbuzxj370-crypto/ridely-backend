package kr.ridely.dao;

import kr.ridely.dto.user.UserSettingsDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 설정 MyBatis Mapper 인터페이스 (ADR-002: 단순 CRUD -> MyBatis).
 * SQL은 resources/mapper/UserSettingsMapper.xml.
 *
 * MyBatisConfig의 @MapperScan("kr.ridely.dao")이 스캔한다.
 *
 * 기본값 1행 INSERT는 회원가입 트랜잭션의 일부라 AuthDao에 남겨 둔다. 여기는 가입 이후의 설정 읽기·쓰기를 담는다.
 */
@Mapper
public interface UserSettingsDao {

    /**
     * 설정 전체를 읽는다.
     *
     * user_id가 UNIQUE라 단건이다. 전 컬럼을 읽지만 PK 단건 조회라 컬럼 하나만 읽는 것과 비용 차이가 없다 — 회피 값 하나가 필요한 곳에서도 이 메서드를 쓴다.
     *
     * @return 설정. 행이 없으면 null. 가입 트랜잭션이 기본값 행을 만드므로 정상 회원에게는 없을 수 없고, null이면 데이터가 어긋난 것이다
     */
    UserSettingsDTO selectById(@Param("userId") long userId);

    /**
     * 설정을 부분 수정한다.
     *
     * null인 필드는 SET 절에서 빠진다. PATCH가 "보낸 항목만 수정"이라 요청에 없던 필드를 그대로 UPDATE하면 기존 값이 지워진다.
     *
     * @return 갱신된 행 수. 0이면 설정 행이 없거나 바꿀 항목이 없었다는 뜻이라 호출부가 존재 확인을 먼저 해야 한다
     */
    int updateSettings(@Param("userId") long userId, @Param("request") UserSettingsDTO request);
}
