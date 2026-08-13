package kr.ridely.service;

import kr.ridely.dao.RepairShopDao;
import kr.ridely.dao.RouteFacilityDao;
import kr.ridely.dto.poi.SeoulFacilityIngestResultDTO;
import kr.ridely.infra.seoul.SeoulBicycleEtcResponse;
import kr.ridely.infra.seoul.SeoulFacilityClassifier;
import kr.ridely.infra.seoul.SeoulFacilityKind;
import kr.ridely.infra.seoul.SeoulOpenApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeoulFacilityIngestServiceImpl implements SeoulFacilityIngestService {

    private static final Logger log = LoggerFactory.getLogger(SeoulFacilityIngestServiceImpl.class);

    /** route_facility.facility_type 값 */
    private static final String FACILITY_TYPE_AIR_PUMP = "AIR_PUMP";

    /** 이 소스는 서울시 데이터라 지역이 고정이다 */
    private static final String REGION_CODE_SEOUL = "11";

    /** 수리범위 상세정보 번호. 검수 목록에 원문을 실어 보내려고 직접 읽는다 */
    private static final String DETAIL_REPAIR_SCOPE = "07";

    private final SeoulOpenApiClient seoulOpenApiClient;
    private final SeoulFacilityClassifier classifier;
    private final RouteFacilityDao routeFacilityDao;
    private final RepairShopDao repairShopDao;

    public SeoulFacilityIngestServiceImpl(SeoulOpenApiClient seoulOpenApiClient,
                                          SeoulFacilityClassifier classifier,
                                          RouteFacilityDao routeFacilityDao,
                                          RepairShopDao repairShopDao) {
        this.seoulOpenApiClient = seoulOpenApiClient;
        this.classifier = classifier;
        this.routeFacilityDao = routeFacilityDao;
        this.repairShopDao = repairShopDao;
    }

    /**
     * 한 트랜잭션으로 두 테이블에 나눠 적재한다.
     * 공기주입기만 들어가고 수리센터는 빠진 중간 상태를 남기지 않기 위해서다.
     */
    @Override
    @Transactional
    public SeoulFacilityIngestResultDTO ingestSeoulFacilities() {
        List<SeoulBicycleEtcResponse.Row> rows = seoulOpenApiClient.fetchAllBicycleEtc();

        Map<SeoulFacilityKind, Integer> classified = new EnumMap<>(SeoulFacilityKind.class);
        List<SeoulFacilityIngestResultDTO.RepairShopSummary> summaries = new ArrayList<>();
        int airPumpInserted = 0;
        int repairInserted = 0;
        int skippedNoCoordinate = 0;

        for (SeoulBicycleEtcResponse.Row row : rows) {
            SeoulFacilityKind kind = classifier.classify(row);
            classified.merge(kind, 1, Integer::sum);
            if (kind == SeoulFacilityKind.OTHER) {
                continue;
            }

            // 좌표가 문자열로 오므로 변환한다. 전량 조사에서 결측 0건이었지만
            // 원본이 바뀔 수 있으니 방어한다 — 좌표 없는 시설은 지도에 못 올린다.
            Double lng = parseCoordinate(row.getLng());
            Double lat = parseCoordinate(row.getLat());
            if (lng == null || lat == null) {
                skippedNoCoordinate++;
                continue;
            }

            String name = trimToNull(row.getContentName());
            if (kind == SeoulFacilityKind.AIR_PUMP) {
                airPumpInserted += routeFacilityDao.insertIgnoringDuplicate(
                        FACILITY_TYPE_AIR_PUMP, name, lng, lat);
                continue;
            }

            boolean free = classifier.isFreeRepair(row);
            String hours = classifier.operatingHours(row);
            repairInserted += repairShopDao.insertIgnoringDuplicate(
                    REGION_CODE_SEOUL, name, lng, lat, resolveAddress(row), free, hours);

            summaries.add(new SeoulFacilityIngestResultDTO.RepairShopSummary(
                    name, resolveAddress(row),
                    classifier.hasRepairScope(row), trimToNull(row.detailValue(DETAIL_REPAIR_SCOPE)),
                    free, hours));
        }

        if (skippedNoCoordinate > 0) {
            log.warn("좌표가 없어 건너뛴 시설 {}건 — 원본에 좌표 결측이 생겼을 수 있다", skippedNoCoordinate);
        }
        log.info("서울시 편의시설 적재 완료: 수집 {}건, 분류 {}, 공기주입기 {}건 INSERT, 수리센터 {}건 INSERT",
                rows.size(), classified, airPumpInserted, repairInserted);

        return new SeoulFacilityIngestResultDTO(
                rows.size(),
                toNameKeyedCount(classified),
                airPumpInserted,
                repairInserted,
                repairShopDao.countAll(),
                repairShopDao.countByFree(),
                summaries);
    }

    /** 도로명주소가 없으면 지번주소로 대체한다. 둘 다 없는 건이 있다(마포구 자전거 수리센터) */
    private String resolveAddress(SeoulBicycleEtcResponse.Row row) {
        String newAddr = trimToNull(row.getNewAddr());
        return newAddr != null ? newAddr : trimToNull(row.getOldAddr());
    }

    private Double parseCoordinate(String value) {
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

    /** enum 키를 JSON에 그대로 내보내려고 문자열 키로 바꾼다 */
    private Map<String, Integer> toNameKeyedCount(Map<SeoulFacilityKind, Integer> classified) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (SeoulFacilityKind kind : SeoulFacilityKind.values()) {
            result.put(kind.name(), classified.getOrDefault(kind, 0));
        }
        return result;
    }
}
