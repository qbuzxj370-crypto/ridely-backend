package kr.ridely.service;

import kr.ridely.dao.TourIngestDao;
import kr.ridely.dto.tour.TourIngestResultDTO;
import kr.ridely.infra.tourapi.TourApiClient;
import kr.ridely.infra.tourapi.TourApiListResponse;
import kr.ridely.infra.tourapi.TourApiProperties;
import kr.ridely.infra.tourapi.TourApiResponseParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한강 서울 구간 관광 콘텐츠 적재 구현.
 *
 * 수집 지점·반경은 application.yml의 ridely.external.tourapi.ingest 에서 받는다.
 * 반경 20km 원 하나로 덮으면 한강과 무관한 서울 전역이 딸려오므로,
 * 한강 축(아라한강갑문~잠실 약 40km)을 따라 중심점을 여러 개 두고 작은 반경으로 훑는다.
 * 지역을 넓힐 때 코드가 아니라 설정만 바꾸면 되도록 분리했다.
 */
@Service
@RequiredArgsConstructor
public class TourIngestServiceImpl implements TourIngestService {

    private static final Logger log = LoggerFactory.getLogger(TourIngestServiceImpl.class);

    /** 한 번에 받아올 항목 수 (매뉴얼상 상한 없음, 응답 크기 고려) */
    private static final int PAGE_SIZE = 100;

    /** 페이지 순회 상한. 무한 루프 방지 */
    private static final int MAX_PAGES = 10;

    /** TourAPI 수정일 형식 (yyyyMMddHHmmss) */
    private static final DateTimeFormatter MODIFIED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TourApiClient tourApiClient;
    private final TourApiResponseParser parser;
    private final TourIngestDao tourIngestDao;
    private final TourApiProperties properties;

    /** region_code → region_id 캐시. 적재 1회 동안만 유지 */
    private final Map<String, Long> regionIdCache = new HashMap<>();

    /**
     * 의도적으로 @Transactional을 붙이지 않는다.
     *
     * 외부 API 호출(수십 회, 수십 초)이 루프 안에 있어 전체를 한 트랜잭션으로 묶으면
     * 그동안 DB 커넥션을 점유한 채 네트워크를 기다리게 된다.
     * UPSERT 1건씩 자동 커밋되며, 각 건이 멱등하므로 중간에 실패해도 재실행하면 된다.
     */
    @Override
    public TourIngestResultDTO ingestHangangArea() {
        regionIdCache.clear();

        List<Integer> contentTypeIds = parseContentTypeIds();
        TourApiProperties.Ingest ingest = properties.ingest();
        int apiCalls = 0, received = 0, upserted = 0, skipped = 0;

        for (TourApiProperties.Point point : ingest.points()) {
            int pointReceived = 0;

            for (int contentTypeId : contentTypeIds) {
                int page = 1;
                while (page <= MAX_PAGES) {
                    String json = tourApiClient.fetchNearbySpots(
                            point.lng(), point.lat(), ingest.radiusM(),
                            contentTypeId, page, PAGE_SIZE);
                    apiCalls++;

                    TourApiListResponse response = parser.parseList(json);
                    List<TourApiListResponse.Item> items = response.items();
                    if (items.isEmpty()) {
                        break;
                    }

                    for (TourApiListResponse.Item item : items) {
                        received++;
                        pointReceived++;
                        if (save(item)) {
                            upserted++;
                        } else {
                            skipped++;
                        }
                    }

                    // 마지막 페이지 도달
                    if (page * PAGE_SIZE >= response.totalCount()) {
                        break;
                    }
                    page++;
                }
            }
            log.info("수집 지점 '{}' 완료 - {}건", point.name(), pointReceived);
        }

        int total = tourIngestDao.countAll();
        log.info("TourAPI 적재 완료 - 호출 {}회, 수신 {}건, 저장 {}건, 건너뜀 {}건, 전체 {}행",
                apiCalls, received, upserted, skipped, total);

        return new TourIngestResultDTO(apiCalls, received, upserted, skipped, total);
    }

    /**
     * 콘텐츠 1건 저장.
     *
     * @return 저장했으면 true, 좌표·필수값이 없어 건너뛰었으면 false
     */
    private boolean save(TourApiListResponse.Item item) {
        Double lng = parseCoordinate(item.getMapx());
        Double lat = parseCoordinate(item.getMapy());

        // geom은 NOT NULL이라 좌표 없는 콘텐츠는 저장할 수 없다
        if (lng == null || lat == null
                || item.getContentid() == null || item.getTitle() == null) {
            log.debug("좌표·필수값 누락으로 건너뜀: contentId={}, title={}",
                    item.getContentid(), item.getTitle());
            return false;
        }

        Long regionId = resolveRegionId(item.getLDongRegnCd());
        tourIngestDao.upsert(item, lng, lat, regionId, parseModifiedTime(item.getModifiedtime()));
        return true;
    }

    /**
     * 법정동 시도 코드 → region_id.
     * 매칭되는 지역이 없으면 null (region_id는 NULL 허용).
     */
    private Long resolveRegionId(String lDongRegnCd) {
        if (lDongRegnCd == null || lDongRegnCd.isBlank()) {
            return null;
        }
        return regionIdCache.computeIfAbsent(lDongRegnCd,
                code -> tourIngestDao.findRegionIdByCode(code).orElse(null));
    }

    /** 좌표는 문자열로 오고, 값이 없으면 빈 문자열이다 */
    private Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 수정일(yyyyMMddHHmmss) → OffsetDateTime. 파싱 실패 시 null */
    private OffsetDateTime parseModifiedTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, MODIFIED_TIME_FORMAT)
                    .atZone(KST)
                    .toOffsetDateTime();
        } catch (Exception e) {
            log.debug("수정일 파싱 실패: {}", value);
            return null;
        }
    }

    /** 설정의 "12,14,39" → [12, 14, 39] */
    private List<Integer> parseContentTypeIds() {
        return Arrays.stream(properties.contentTypeIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .toList();
    }
}
