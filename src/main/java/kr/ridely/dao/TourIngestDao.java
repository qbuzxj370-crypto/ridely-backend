package kr.ridely.dao;

import kr.ridely.infra.tourapi.TourApiListResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
    
/**
 * TourAPI 관광 콘텐츠 적재 DAO.
 *
 * PostGIS 함수(ST_SetSRID·ST_MakePoint)를 쓰므로 MyBatis가 아닌 JdbcClient를 사용한다.
 */
@Repository
public class TourIngestDao {

    private final JdbcClient jdbcClient;

    public TourIngestDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 관광 콘텐츠 1건 UPSERT.
     *
     * content_id UNIQUE 제약을 이용해 재적재해도 중복이 생기지 않는다.
     * 이미 있는 콘텐츠는 최신 값으로 갱신한다 (TourAPI 원본이 수정될 수 있으므로).
     *
     * 공식 명세에서 삭제된 항목(area_code·sigungu_code·cat1~3)은 채우지 않는다.
     * 하위 호환용으로 응답에 남아 있을 뿐이라 언제든 사라질 수 있으므로 사용을 지양한다.
     *
     * @return 영향받은 행 수 (1)
     */
    public int upsert(TourApiListResponse.Item item,
                      double lng, double lat,
                      Long regionId,
                      OffsetDateTime sourceModifiedAt) {

        /*
         * [쿼리의 목적]
         *   관광지 1건을 넣되 같은 콘텐츠가 이미 있으면 최신 값으로 덮어쓴다(UPSERT).
         *   재적재를 몇 번 실행해도 행이 늘어나지 않게 만드는 것이 목적
         *
         * [동작 순서]
         *   1) INSERT 시도
         *   2) content_id가 이미 있으면(UNIQUE 위반) 오류 대신 UPDATE로 전환 → ON CONFLICT
         *
         * [핵심 구문]
         *   ST_MakePoint(경도, 위도)  경도·위도로 점(Point) 생성. 위도·경도 순서가 아님에 주의
         *   ST_SetSRID(..., 4326)     그 점이 WGS84(일반 위경도) 좌표계임을 명시.
         *                             지정하지 않으면 좌표계 미상이 되어 거리 계산이 불가능하다
         *   ON CONFLICT (content_id)  UNIQUE 컬럼 충돌 시 처리 방법 지정
         *   EXCLUDED.컬럼             이번에 넣으려다 충돌한 "새 값". 즉 새 값으로 갱신하라는 뜻
         *
         * [채우지 않는 컬럼]
         *   area_code·sigungu_code·cat1~3 : 공식 명세에서 삭제된 항목이라 사용하지 않음
         *   created_at·tour_attraction_id : DB 기본값·시퀀스가 채움
         *   overview                      : 목록 응답에 없음. 상세 조회(detailCommon2)로 별도 보충
         */
        String sql = """
                INSERT INTO tour_attraction (
                    region_id,          -- 지역 (법정동 시도 코드로 찾은 값, 없으면 NULL)
                    content_id,         -- TourAPI 콘텐츠 ID. 중복 판단 기준 (UNIQUE)
                    content_type_id,    -- 12=관광지 14=문화시설 39=음식점
                    title,              -- 관광지명
                    addr1, addr2,       -- 주소 / 상세주소
                    geom,               -- 위치 (Point, SRID 4326)
                    tel,                -- 전화번호
                    first_image_url,    -- 대표이미지 원본
                    thumbnail_url,      -- 대표이미지 썸네일
                    source_modified_at  -- TourAPI 원본 수정일. 증분 갱신 판단 기준
                ) VALUES (
                    :regionId, :contentId, :contentTypeId, :title,
                    :addr1, :addr2,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                    :tel, :firstImageUrl, :thumbnailUrl, :sourceModifiedAt
                )
                -- 같은 content_id가 이미 있으면 INSERT 대신 아래 UPDATE 수행
                ON CONFLICT (content_id) DO UPDATE SET
                    region_id          = EXCLUDED.region_id,
                    content_type_id    = EXCLUDED.content_type_id,
                    title              = EXCLUDED.title,
                    addr1              = EXCLUDED.addr1,
                    addr2              = EXCLUDED.addr2,
                    geom               = EXCLUDED.geom,
                    tel                = EXCLUDED.tel,
                    first_image_url    = EXCLUDED.first_image_url,
                    thumbnail_url      = EXCLUDED.thumbnail_url,
                    source_modified_at = EXCLUDED.source_modified_at,
                    updated_at         = NOW()   -- 우리 쪽 갱신 시각 (원본 수정일과 별개)
                """;

        return jdbcClient.sql(sql)
                .param("regionId", regionId)
                .param("contentId", item.getContentid())
                .param("contentTypeId", item.getContenttypeid())
                .param("title", item.getTitle())
                .param("addr1", emptyToNull(item.getAddr1()))
                .param("addr2", emptyToNull(item.getAddr2()))
                .param("lng", lng)
                .param("lat", lat)
                .param("tel", emptyToNull(item.getTel()))
                .param("firstImageUrl", emptyToNull(item.getFirstimage()))
                .param("thumbnailUrl", emptyToNull(item.getFirstimage2()))
                .param("sourceModifiedAt", sourceModifiedAt)
                .update();
    }

    /**
     * 법정동 시도 코드로 region_id 조회.
     *
     * TourAPI의 lDongRegnCd는 행정표준 시도 코드라 region.region_code와 값이 일치한다.
     * (구 areacode는 서울이 "1"이라 별도 매핑표가 필요했다)
     *
     * @return 매칭되는 지역이 없으면 empty — tour_attraction.region_id는 NULL 허용
     */
    public Optional<Long> findRegionIdByCode(String regionCode) {
        return jdbcClient.sql("SELECT region_id FROM region WHERE region_code = :code")
                .param("code", regionCode)
                .query(Long.class)
                .optional();
    }

    /** 적재 결과 확인용 전체 건수 */
    public int countAll() {
        return jdbcClient.sql("SELECT COUNT(*) FROM tour_attraction")
                .query(Integer.class)
                .single();
    }

    /** TourAPI는 값이 없을 때 null이 아니라 빈 문자열을 보낸다 */
    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
