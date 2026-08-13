package kr.ridely.service;

import kr.ridely.config.MvpAreaProperties;
import kr.ridely.dao.AccidentZoneDao;
import kr.ridely.dto.poi.AccidentZoneIngestResultDTO;
import kr.ridely.infra.taas.SeoulDistrict;
import kr.ridely.infra.taas.TaasClient;
import kr.ridely.infra.taas.TaasFrequentZoneResponse;
import kr.ridely.infra.taas.TaasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccidentZoneIngestServiceImpl implements AccidentZoneIngestService {

    private static final Logger log = LoggerFactory.getLogger(AccidentZoneIngestServiceImpl.class);

    /**
     * 위험 등급 경계. schema 주석의 파생 규칙을 그대로 옮긴 것이다.
     *   DANGER  : 10건 이상 또는 사망사고 포함
     *   WARNING : 6~9건
     *   CAUTION : 그 외 (선정 기준상 4~5건, 사망 포함이면 3건도 가능)
     */
    private static final int DANGER_OCCURRENCE_THRESHOLD = 10;
    private static final int WARNING_OCCURRENCE_THRESHOLD = 6;

    private static final String LEVEL_DANGER = "DANGER";
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_CAUTION = "CAUTION";

    /** 법정동코드 앞 2자리가 시도코드다. region 매핑에 쓴다 */
    private static final int SIDO_CODE_LENGTH = 2;

    /** 법정동코드 앞 5자리가 시군구다. 1168011800 → 11680 강남구 */
    private static final int SIGUNGU_CODE_LENGTH = 5;

    private final TaasClient taasClient;
    private final TaasProperties taasProperties;
    private final AccidentZoneDao accidentZoneDao;
    private final MvpAreaProperties mvpArea;

    public AccidentZoneIngestServiceImpl(TaasClient taasClient,
                                         TaasProperties taasProperties,
                                         AccidentZoneDao accidentZoneDao,
                                         MvpAreaProperties mvpArea) {
        this.taasClient = taasClient;
        this.taasProperties = taasProperties;
        this.accidentZoneDao = accidentZoneDao;
        this.mvpArea = mvpArea;
    }

    /** 25개 자치구를 한 트랜잭션으로 적재한다. 일부 구만 들어간 상태를 남기지 않는다 */
    @Override
    @Transactional
    public AccidentZoneIngestResultDTO ingestAccidentZones(Integer year) {
        int dataYear = year != null ? year : taasProperties.searchYear();
        List<TaasFrequentZoneResponse.Item> items = taasClient.fetchSeoulZones(dataYear);

        int upserted = 0;
        int skippedNoCoordinate = 0;
        int skippedUnknownRegion = 0;
        int missingPolygon = 0;

        for (TaasFrequentZoneResponse.Item item : items) {
            Double lng = parseDouble(item.getLoCrd());
            Double lat = parseDouble(item.getLaCrd());
            if (lng == null || lat == null || item.getAfosFid() == null) {
                skippedNoCoordinate++;
                continue;
            }

            // sido_sgg_nm이 아니라 법정동코드로 지역을 잡는다.
            // 표기가 연도별로 다르기 때문이다 — 2023 "서울 강남구1" vs 2022 "서울특별시 강남구1".
            String regionCode = sidoCodeOf(item.getBjdCd());
            if (regionCode == null) {
                skippedUnknownRegion++;
                continue;
            }

            String geoJson = trimToNull(item.getGeomJson());
            if (geoJson == null) {
                missingPolygon++;
            }

            upserted += accidentZoneDao.upsert(
                    regionCode,
                    String.valueOf(item.getAfosFid()),
                    item.getBjdCd(),
                    item.getSpotCd(),
                    item.getSidoSggNm(),
                    item.getSpotNm(),
                    orZero(item.getOccrrncCnt()),
                    orZero(item.getCasltCnt()),
                    orZero(item.getDthDnvCnt()),
                    orZero(item.getSeDnvCnt()),
                    orZero(item.getSlDnvCnt()),
                    orZero(item.getWndDnvCnt()),
                    dangerLevelOf(orZero(item.getOccrrncCnt()), orZero(item.getDthDnvCnt())),
                    dataYear,
                    lng, lat, geoJson);
        }

        int mvpAreaCount = accidentZoneDao.countInMvpArea(
                mvpArea.minLng(), mvpArea.maxLng(), mvpArea.minLat(), mvpArea.maxLat());
        int districtsWithData = countDistrictsWithData(items);

        if (skippedNoCoordinate > 0 || skippedUnknownRegion > 0) {
            log.warn("사고다발지 건너뜀 — 좌표 없음 {}건, 지역 미상 {}건",
                    skippedNoCoordinate, skippedUnknownRegion);
        }
        log.info("사고다발지 적재 완료: {}년 수집 {}건 (자치구 {}/{}), UPSERT {}건, "
                        + "폴리곤 없음 {}건, MVP 구간 {}건",
                dataYear, items.size(), districtsWithData, SeoulDistrict.all().size(),
                upserted, missingPolygon, mvpAreaCount);

        return new AccidentZoneIngestResultDTO(
                dataYear,
                items.size(),
                districtsWithData,
                upserted,
                skippedNoCoordinate,
                skippedUnknownRegion,
                missingPolygon,
                accidentZoneDao.countAll(),
                accidentZoneDao.countByDangerLevel(),
                accidentZoneDao.countByGeometryType(),
                mvpAreaCount);
    }

    /**
     * 위험 등급 파생.
     *
     * 사망사고를 건수보다 앞에 둔다. 선정 기준이 "4건 이상, 단 사망사고 포함 시 3건 이상"이라
     * 사망이 있으면 건수가 적어도 위험도가 높다.
     */
    private String dangerLevelOf(int occurrenceCount, int deathCount) {
        if (deathCount > 0 || occurrenceCount >= DANGER_OCCURRENCE_THRESHOLD) {
            return LEVEL_DANGER;
        }
        if (occurrenceCount >= WARNING_OCCURRENCE_THRESHOLD) {
            return LEVEL_WARNING;
        }
        return LEVEL_CAUTION;
    }

    /**
     * 데이터가 있던 자치구 수.
     *
     * 법정동코드 앞 5자리가 시군구다(1168011800 → 11680 강남구).
     * 사고다발지가 없는 자치구는 NODATA로 응답해 목록에 아예 안 들어온다.
     */
    private int countDistrictsWithData(List<TaasFrequentZoneResponse.Item> items) {
        return (int) items.stream()
                .map(TaasFrequentZoneResponse.Item::getBjdCd)
                .filter(code -> code != null && code.length() >= SIGUNGU_CODE_LENGTH)
                .map(code -> code.substring(0, SIGUNGU_CODE_LENGTH))
                .distinct()
                .count();
    }

    /** 법정동코드 앞 2자리 = 시도코드. 1168011800 → 11(서울) */
    private String sidoCodeOf(String bjdCd) {
        if (bjdCd == null || bjdCd.length() < SIDO_CODE_LENGTH) {
            return null;
        }
        return bjdCd.substring(0, SIDO_CODE_LENGTH);
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
