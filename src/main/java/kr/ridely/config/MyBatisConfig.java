package kr.ridely.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 설정.
 * dao 패키지의 @Mapper 인터페이스를 스캔한다.
 *
 * Mapper XML 위치·camelCase 매핑 등은 application.yml의 mybatis.* 에서 설정.
 *
 * 주의: PostGIS·pgvector 쿼리는 MyBatis가 아닌 JdbcClient로 작성 (ADR-002 사용 규칙).
 */
@Configuration
@MapperScan("kr.ridely.dao")
public class MyBatisConfig {
    // @MapperScan만으로 충분. 추가 TypeHandler 등록이 필요해지면 여기에.
}
