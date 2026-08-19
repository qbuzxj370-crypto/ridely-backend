package kr.ridely.dao;

import kr.ridely.config.MvpAreaProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 국토종주 자전거길 노선 DAO.
 *
 * PostGIS 함수(ST_GeomFromText·ST_Length·ST_DumpPoints)를 사용하므로 MyBatis가 아닌 JdbcClient를 사용한다 (ADR-002).
 */
@Repository
public class NationalBikeRouteDao {

    /**
     * 연장점 거리 허용 폭.
     *
     * 노선 좌표는 이미 찍혀 있는 점들이라 원하는 거리에 정확히 놓인 점이 있으리라는 보장이 없다. ±10% 안에서 찾는다.
     */
    private static final double DISTANCE_BAND = 0.1;

    private final JdbcClient jdbcClient;
    private final MvpAreaProperties mvpArea;

    public NationalBikeRouteDao(JdbcClient jdbcClient, MvpAreaProperties mvpArea) {
        this.jdbcClient = jdbcClient;
        this.mvpArea = mvpArea;
    }

    /**
     * 자전거도로 위에서 출발지로부터 일정 거리 떨어진 점을 찾는다. 코스가 목표 거리에 못 미칠 때 앞쪽에 붙여 왕복으로 거리를 늘리는 데 쓴다.
     *
     * 방향은 도착지 반대쪽이다. 도착지 쪽으로 연장하면 그냥 지름길이 되어 거리가 늘지 않는다. 후보 점들 중 도착지에서 가장 먼 것을 고르는 방식으로 방향을 정한다. 순환 코스는 도착지가 출발지와 같아 결과적으로 가장 멀리 나가는 점이 뽑힌다.
     *
     * ⚠️ ST_LineInterpolatePoint를 쓰지 않는다. line_geom이 MULTILINESTRING이고 저 함수는 LINESTRING만 받는다. ST_LineMerge로 합칠 수는 있지만 노선이 끊겨 있으면 여전히 MULTILINESTRING이 나와 갈래 선택 문제가 남는다. 노선 좌표를 그대로 풀어 거리로 거르는 편이 단순하고 형상 종류를 안 탄다.
     *
     * 서비스 지역 밖은 제외한다. 목표 거리가 크면 요구 거리도 커져서 노선을 따라 서울 밖까지 나갈 수 있다.
     *
     * @param awayFromLng 이 지점에서 멀어지는 방향으로 찾는다. 보통 도착지
     * @param extensionM  출발지에서 이만큼 떨어진 점을 찾는다
     * @return [경도, 위도]. 조건에 맞는 점이 없으면 비어 있다
     */
    public Optional<double[]> findExtensionPoint(double startLng, double startLat,
                                                 double awayFromLng, double awayFromLat,
                                                 int extensionM) {
        /*
         * [핵심 구문]
         *   <->                거리 순 정렬 연산자. 출발지에서 가장 가까운 노선 하나만 고른다.
         *                      이 제한이 없으면 전국 노선의 좌표를 전부 풀게 된다.
         *   ST_DumpPoints      노선 형상을 좌표 하나하나로 푼다. MULTILINESTRING도 그대로 받는다.
         *   LATERAL            앞 CTE의 형상을 인자로 넘겨야 하므로 필요하다.
         *   ST_MakeEnvelope    서비스 지역 사각형. 좌표가 이 밖이면 버린다.
         */
        String sql = """
                WITH anchor AS (
                    SELECT ST_SetSRID(ST_MakePoint(:startLng,    :startLat),    4326) AS s,
                           ST_SetSRID(ST_MakePoint(:awayFromLng, :awayFromLat), 4326) AS a
                ),
                nearest_route AS (
                    SELECT r.line_geom
                    FROM national_bike_route r, anchor
                    ORDER BY r.line_geom <-> anchor.s
                    LIMIT 1
                )
                SELECT ST_X(p.geom) AS lng, ST_Y(p.geom) AS lat
                FROM nearest_route, anchor, LATERAL ST_DumpPoints(nearest_route.line_geom) p
                WHERE ST_DWithin(p.geom::geography, anchor.s::geography, :maxM)
                  AND ST_Distance(p.geom::geography, anchor.s::geography) >= :minM
                  AND p.geom && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
                ORDER BY ST_Distance(p.geom::geography, anchor.a::geography) DESC
                LIMIT 1
                """;

        return jdbcClient.sql(sql)
                .param("startLng", startLng)
                .param("startLat", startLat)
                .param("awayFromLng", awayFromLng)
                .param("awayFromLat", awayFromLat)
                .param("minM", extensionM * (1 - DISTANCE_BAND))
                .param("maxM", extensionM * (1 + DISTANCE_BAND))
                .param("minLng", mvpArea.minLng())
                .param("minLat", mvpArea.minLat())
                .param("maxLng", mvpArea.maxLng())
                .param("maxLat", mvpArea.maxLat())
                .query((rs, rowNum) -> new double[]{rs.getDouble("lng"), rs.getDouble("lat")})
                .optional();
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
