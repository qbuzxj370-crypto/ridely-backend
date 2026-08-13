package kr.ridely.dao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * 국토종주 자전거길 노선 DAO.
 *
 * PostGIS 함수(ST_GeomFromText·ST_Length)를 사용하므로 MyBatis가 아닌 JdbcClient를 사용한다 (ADR-002).
 */
@Repository
public class NationalBikeRouteDao {

    private final JdbcClient jdbcClient;

    public NationalBikeRouteDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 노선 하나를 저장한다. 이미 있는 노선명이면 갱신한다.
     *
     * @param routeName        노선명 (UNIQUE 키)
     * @param startDesc        출발 지점 설명. 원본에 없으면 null
     * @param endDesc          도착 지점 설명. 원본에 없으면 null
     * @param officialLengthKm 자전거행복나눔 공식 안내 거리(km). 계산값이 아니다
     * @param multiLineWkt     MULTILINESTRING((경도 위도, ...), (...)) 형식의 노선 형상
     * @return 저장된 형상에서 계산한 실측 길이(km). 저장값이 아니라 검증용이다
     */
    public BigDecimal upsert(String routeName, String startDesc, String endDesc,
                             BigDecimal officialLengthKm, String multiLineWkt) {

        /*
         * [무엇을 하는 쿼리인가]
         *   구간별로 나눈 선을 노선 한 행으로 저장하고, 저장된 형상의 실측 길이를 돌려받는다.
         *
         * [핵심 구문]
         *   ST_GeomFromText(wkt, 4326)  WKT 문자열을 지오메트리로 변환. 4326은 WGS84 경위도.
         *                               좌표가 수천 개라 파라미터로 넘기는 편이 SQL 조립보다 안전하다.
         *   ::geography                 도 단위 geometry를 미터 단위로 해석하게 바꾼다.
         *                               이걸 붙여야 ST_Length가 미터를 돌려준다.
         *   ON CONFLICT (route_name)    route_name UNIQUE 제약을 이용한 멱등 저장.
         *                               재적재해도 행이 늘지 않는다.
         *   RETURNING                   방금 저장된 형상의 길이를 계산해 적재 결과 요약에 쓴다.
         *
         * [total_length_km에 계산값을 넣지 않는 이유]
         *   계산값은 노선마다 담긴 범위가 달라 기준이 제각각이다
         *   (한강종주 0.49배·북한강 1.61배·동해안 경북 1.62배).
         *   공식 안내 거리를 저장하고, 형상 길이가 필요하면 ST_Length로 그때 구한다.
         *   상세: docs/shared/SCHEMA_CHANGE_ROUTE_GEOM.md 8장
         *
         * [주의]
         *   WKT는 "경도 위도" 순서다. 위도를 먼저 쓰면 조용히 엉뚱한 좌표가 된다.
         *   ST_Length는 MultiLineString의 파트 합을 돌려준다. 파트 사이 간격은 세지 않는다.
         */
        String sql = """
                INSERT INTO national_bike_route (
                    route_name, start_desc, end_desc, total_length_km, line_geom
                ) VALUES (
                    :routeName,
                    :startDesc,
                    :endDesc,
                    :officialLengthKm,
                    ST_GeomFromText(:multiLineWkt, 4326)
                )
                ON CONFLICT (route_name) DO UPDATE SET
                    start_desc      = EXCLUDED.start_desc,
                    end_desc        = EXCLUDED.end_desc,
                    total_length_km = EXCLUDED.total_length_km,
                    line_geom       = EXCLUDED.line_geom
                RETURNING ROUND((ST_Length(line_geom::geography) / 1000)::numeric, 1)
                """;

        return jdbcClient.sql(sql)
                .param("routeName", routeName)
                .param("startDesc", startDesc)
                .param("endDesc", endDesc)
                .param("officialLengthKm", officialLengthKm)
                .param("multiLineWkt", multiLineWkt)
                .query(BigDecimal.class)
                .single();
    }
}
