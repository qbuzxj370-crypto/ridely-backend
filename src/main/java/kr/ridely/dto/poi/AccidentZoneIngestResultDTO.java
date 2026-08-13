package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 자전거 사고다발지역 적재 결과.
 * POST /api/v1/poc/taas/ingest 응답.
 *
 * 확인할 것
 *   - `geometryTypes`에 ST_MultiPolygon만 있어야 한다 (V3에서 통일했다)
 *   - `mvpAreaCount`는 경계 상자 안의 건수지 코스에 걸리는 건수가 아니다
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccidentZoneIngestResultDTO {

    /** 조회한 사고년도 */
    private int dataYear;

    /** API에서 수집한 건수 (서울 25개 자치구 합) */
    private int fetchedCount;

    /**
     * 데이터가 있던 자치구 수 / 25.
     *
     * 사고다발지가 없는 자치구는 resultCode "03"(NODATA)로 응답한다.
     * 이 값이 낮으면 그해 자전거 사고다발지 자체가 적다는 뜻이다 — 희소성 지표.
     */
    private int districtsWithData;

    /** UPSERT된 건수 */
    private int upsertedCount;

    /** 좌표가 없어 건너뛴 건수. 0이 정상 */
    private int skippedNoCoordinate;

    /** 지역 코드를 찾지 못해 건너뛴 건수. 서울만 조회하므로 0이 정상 */
    private int skippedUnknownRegion;

    /** 폴리곤이 없어 center_geom만 저장한 건수 */
    private int missingPolygonCount;

    /** 테이블 전체 건수 (다른 연도 포함) */
    private int totalCount;

    /** 등급별 건수. CAUTION(4~5) / WARNING(6~9) / DANGER(10+ 또는 사망 포함) */
    private Map<String, Integer> countByDangerLevel;

    /** 폴리곤 지오메트리 타입 분포. ST_MultiPolygon만 나와야 한다 */
    private Map<String, Integer> geometryTypes;

    /**
     * MVP 경계 상자(`ridely.mvp-area`) 안에 든 건수.
     *
     * ⚠️ "한강 자전거도로 위"가 아니다. 상자가 서울 도심 대부분을 덮으므로
     * 이 값은 크게 나온다(2024년 111건 중 73건). 회피 대상 건수는 노선 형상에서
     * 반경을 잡아 따로 세야 한다. 여기서는 적재 검증 지표로만 쓴다.
     */
    private int mvpAreaCount;
}
