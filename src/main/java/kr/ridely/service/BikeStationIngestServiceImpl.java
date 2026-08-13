package kr.ridely.service;

import kr.ridely.dao.BikeStationDao;
import kr.ridely.dto.poi.BikeStationIngestResultDTO;
import kr.ridely.infra.seoul.SeoulOpenApiClient;
import kr.ridely.infra.seoul.SeoulStationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BikeStationIngestServiceImpl implements BikeStationIngestService {

    private static final Logger log = LoggerFactory.getLogger(BikeStationIngestServiceImpl.class);

    /** 이 소스는 서울시 데이터라 지역이 고정이다 */
    private static final String REGION_CODE_SEOUL = "11";

    private final SeoulOpenApiClient seoulOpenApiClient;
    private final BikeStationDao bikeStationDao;

    public BikeStationIngestServiceImpl(SeoulOpenApiClient seoulOpenApiClient,
                                        BikeStationDao bikeStationDao) {
        this.seoulOpenApiClient = seoulOpenApiClient;
        this.bikeStationDao = bikeStationDao;
    }

    /**
     * 한 트랜잭션으로 처리한다.
     *
     * 비활성 판정이 {@code updated_at >= NOW()}에 기대는데, PostgreSQL의 {@code NOW()}는
     * 트랜잭션 시작 시각이라 같은 트랜잭션 안에서 값이 고정된다.
     * 트랜잭션을 나누면 upsert 시각이 제각각이 되어 판정이 깨진다.
     */
    @Override
    @Transactional
    public BikeStationIngestResultDTO ingestBikeStations() {
        List<SeoulStationResponse.Row> rows = seoulOpenApiClient.fetchAllStations();

        int upserted = 0;
        int skippedNoCoordinate = 0;

        for (SeoulStationResponse.Row row : rows) {
            Double lng = parseDouble(row.getLng());
            Double lat = parseDouble(row.getLat());
            String code = trimToNull(row.getRentId());
            String name = trimToNull(row.getRentName());

            // 좌표나 고유키가 없으면 지도에 올릴 수도, 갱신을 추적할 수도 없다.
            // 전량 조사에서 결측 0건이었지만 원본이 바뀔 수 있어 방어한다.
            if (lng == null || lat == null || code == null || name == null) {
                skippedNoCoordinate++;
                continue;
            }

            upserted += bikeStationDao.upsert(
                    REGION_CODE_SEOUL, code, name, lng, lat, parseInteger(row.getHoldNum()));
        }

        int activationChanged = bikeStationDao.deactivateStale();

        if (skippedNoCoordinate > 0) {
            log.warn("좌표 또는 식별자가 없어 건너뛴 대여소 {}건 — 원본에 결측이 생겼을 수 있다",
                    skippedNoCoordinate);
        }
        log.info("따릉이 대여소 적재 완료: 수집 {}건, UPSERT {}건, 활성 상태 변경 {}건",
                rows.size(), upserted, activationChanged);

        return new BikeStationIngestResultDTO(
                rows.size(),
                upserted,
                skippedNoCoordinate,
                bikeStationDao.countAll(),
                bikeStationDao.countActive(),
                activationChanged);
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

    /** 거치대 수. 없거나 숫자가 아니면 null로 둔다 — 컬럼이 nullable이다 */
    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
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
