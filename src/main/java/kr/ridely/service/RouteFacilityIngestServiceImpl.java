package kr.ridely.service;

import kr.ridely.dao.RouteFacilityDao;
import kr.ridely.dto.poi.RouteFacilityIngestResultDTO;
import kr.ridely.infra.seed.RouteFacilityCsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteFacilityIngestServiceImpl implements RouteFacilityIngestService {

    private static final Logger log = LoggerFactory.getLogger(RouteFacilityIngestServiceImpl.class);

    /**
     * 시설을 노선에 연결할 때의 최대 거리 (m).
     *
     * 원본 CSV에 노선 코드가 없어 좌표로 추정한다. 화장실·급수대는 자전거길 바로 옆에 있지만
     * 노선 형상 자체가 파트로 갈려 있고 좌표 간격도 일정하지 않아 여유를 둔다.
     * 이 안에 노선이 없으면 연결하지 않고 NULL로 남긴다.
     *
     * <p>연결률은 적재 응답의 linkedCount로 확인한다. 지나치게 낮으면 이 값을 조정하되,
     * 넓힐수록 엉뚱한 노선에 붙을 위험이 커진다는 점을 감안한다.
     */
    private static final int LINK_RADIUS_M = 500;

    private final RouteFacilityCsvReader csvReader;
    private final RouteFacilityDao routeFacilityDao;

    public RouteFacilityIngestServiceImpl(RouteFacilityCsvReader csvReader,
                                          RouteFacilityDao routeFacilityDao) {
        this.csvReader = csvReader;
        this.routeFacilityDao = routeFacilityDao;
    }

    /**
     * 한 트랜잭션으로 적재하고 연결한다.
     * 시설만 들어가고 연결이 안 된 중간 상태를 남기지 않기 위해서다.
     */
    @Override
    @Transactional
    public RouteFacilityIngestResultDTO ingestRouteFacilities() {
        RouteFacilityCsvReader.ParseResult parsed = csvReader.readFacilities();

        int inserted = routeFacilityDao.insertIgnoringDuplicates(parsed.getFacilities());

        // 재실행 시 inserted가 0이어도 연결은 다시 계산한다.
        // 노선을 나중에 적재했거나 형상을 바꿨으면 연결이 달라지기 때문이다.
        routeFacilityDao.linkToNearestRoute(LINK_RADIUS_M);
        int linked = routeFacilityDao.countLinked();

        log.info("주변시설 적재 완료: {}건 INSERT (중복 {}건 제외), 노선 연결 {}건 (반경 {}m)", inserted, parsed.getDuplicateRowCount(), linked, LINK_RADIUS_M);

        return new RouteFacilityIngestResultDTO(
                parsed.getCharsetName(),
                parsed.getDataRowCount(),
                parsed.getSkippedRowCount(),
                parsed.getDuplicateRowCount(),
                parsed.getUnknownLabelCount(),
                inserted,
                routeFacilityDao.countByType(),
                linked,
                LINK_RADIUS_M);
    }
}
