package kr.ridely.dao;

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
     * 사고다발지 회피 설정을 읽는다.
     *
     * 반환 타입이 Boolean인 이유는 "설정이 꺼져 있다"와 "설정 행이 없다"를 구분해야 해서다. 가입 트랜잭션이 기본값 행을 만들므로 정상 회원에게는 항상 값이 있지만, 그 트랜잭션 이전에 만들어진 계정이나 데이터 손상으로 행이 없을 수 있다. null을 false로 뭉개면 그 상황이 드러나지 않는다.
     *
     * @return 설정값. 설정 행이 없으면 null
     */
    Boolean selectAvoidDangerZones(@Param("userId") long userId);
}
