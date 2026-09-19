package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.config.MvpAreaProperties;
import kr.ridely.dao.AccidentZoneSpatialDao;
import kr.ridely.dao.PoiSpatialDao;
import kr.ridely.dto.poi.PoiAllResponseDTO;
import kr.ridely.dto.poi.PoiItemDTO;
import kr.ridely.dto.poi.PoiNearbyResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 인프라 POI 조회 구현.
 *
 * <h3>0건과 서비스 지역 밖을 가른다</h3>
 *
 * 둘 다 「아무것도 안 나온다」로 보이지만 사용자가 할 일이 다르다. 지역 안에서 0건이면 지도를 조금 옮기거나 반경을 넓히면 된다. 지역 밖이면 얼마를 움직여도 안 나온다.
 *
 * <pre>
 * 서비스 지역 밖   ROUTE-003     이 지역은 아직 지원하지 않는다
 * 지역 안 0건      200 + 빈 배열  이 레이어의 평소 상태다
 * </pre>
 *
 * <b>0건을 에러로 내지 않는 근거는 측정이다.</b> 서비스 지역을 450m 격자로 훑어 반경 1km 안의 건수를 세었다(2026-09-15).
 *
 * <pre>
 * 따릉이  3,239건 적재   0건 비율 16.8%
 * 급수대    185건        0건 비율 84.4%
 * 수리소     24건        0건 비율 93.8%
 * </pre>
 *
 * 수리소 레이어는 지도를 어디에 놓든 거의 비어 있다. 404로 내면 정상 동작의 대부분이 에러가 된다.
 *
 * ⚠️ 같은 이유로 <b>반경 기본값 1,000m는 조밀한 종류에나 맞는다.</b> 수리소·사고다발지는 상한인 5,000m가 사실상 최소값이다. 이 판단은 화면이 종류별로 해야 하므로 프론트 가이드에 적재 건수를 함께 싣는다.
 */
@Service
@RequiredArgsConstructor
public class PoiServiceImpl implements PoiService {

    /** route_facility 테이블에 있는 시설 종류. 이 값들은 하나의 조회로 묶인다 */
    private static final Set<String> FACILITY_TYPES =
            Set.of("WATER", "TOILET", "CERT_CENTER", "AIR_PUMP");

    private static final String TYPE_REPAIR_SHOP = "REPAIR_SHOP";
    private static final String TYPE_BIKE_STATION = "BIKE_STATION";
    private static final String TYPE_ACCIDENT_ZONE = "ACCIDENT_ZONE";

    /** types 미지정 시 조회할 전체 종류 */
    private static final List<String> DEFAULT_TYPES = List.of(
            "WATER", "TOILET", "CERT_CENTER", "AIR_PUMP",
            TYPE_REPAIR_SHOP, TYPE_BIKE_STATION, TYPE_ACCIDENT_ZONE);

    /**
     * 종류마다의 조회 상한.
     *
     * 합산 상한이 아니라 종류별 상한이다. 하나로 묶으면 따릉이가 목록을 채워 수리소가 밀려난다 - 적재 건수가 3,239 대 24라 거리순으로만 자르면 희소한 종류가 통째로 사라진다. 레이어를 켠 이유가 그 종류를 보려는 것이므로 그러면 안 된다.
     */
    private static final int MAX_PER_TYPE = 100;

    private static final Logger log = LoggerFactory.getLogger(PoiServiceImpl.class);

    /**
     * 전체 조회 응답이 이 건수를 넘으면 경고를 남긴다.
     *
     * 전체 조회는 항상 전부를 내려주는 구조라 적재가 늘면 모든 사용자의 다운로드가 그만큼
     * 커진다. 2026-09-19 기준 약 5,500건이다. 이 값은 실패시키는 한도가 아니라 「이제 구조를
     * 다시 볼 때」를 알리는 신호다 - 넘으면 응답 슬림화·정적 파일 배포 등을 검토한다
     * (docs/shared/0919/NEARBY_INFRA_LOCAL_PLAN.md).
     */
    static final int ALL_ITEMS_WARN_THRESHOLD = 10_000;

    /**
     * 전체 조회 결과를 서버 메모리에 두는 시간(초). 0이면 캐시하지 않는다.
     *
     * 이 API는 인증 없이 열려 있고(PUBLIC_PATHS), 백엔드에는 요청 제한이 따로 없다. 요청마다 네 테이블을
     * 전부 읽고 약 525KB를 직렬화하는 구조라, 캐시가 없으면 반복 요청이 그대로 DB 부담이 된다.
     * 적재 데이터는 배치로 가끔만 바뀌고 응답 헤더(Cache-Control 1시간)도 이미 그만큼의 낡음을
     * 허용하므로, 서버에서 몇 분 들고 있는 것은 새로 생기는 손해가 없다.
     *
     * 단, 적재 직후에도 이 시간까지는 옛 목록이 나간다. 테스트는 이 값을 0으로 둔다
     * (AbstractIntegrationTest) — 데이터를 바꾸고 바로 응답을 확인하기 때문이다.
     */
    @Value("${ridely.poi.all-cache-seconds:300}")
    private long allCacheSeconds;

    private volatile PoiAllResponseDTO allCache;
    private volatile long allCacheLoadedNanos;

    private final PoiSpatialDao poiSpatialDao;
    private final AccidentZoneSpatialDao accidentZoneSpatialDao;
    private final MvpAreaProperties mvpArea;

    @Override
    public PoiNearbyResponseDTO findNearby(double lat, double lng, int radiusM, List<String> types) {
        if (!mvpArea.contains(lng, lat)) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }

        List<String> requested = (types == null || types.isEmpty()) ? DEFAULT_TYPES : types;
        List<PoiItemDTO> items = new ArrayList<>();

        List<String> facilityTypes = requested.stream().filter(FACILITY_TYPES::contains).toList();
        if (!facilityTypes.isEmpty()) {
            items.addAll(poiSpatialDao.findRouteFacilitiesInRadius(
                    lng, lat, radiusM, facilityTypes, MAX_PER_TYPE));
        }
        if (requested.contains(TYPE_REPAIR_SHOP)) {
            items.addAll(poiSpatialDao.findRepairShopsInRadius(lng, lat, radiusM, MAX_PER_TYPE));
        }
        if (requested.contains(TYPE_BIKE_STATION)) {
            items.addAll(poiSpatialDao.findBikeStationsInRadius(lng, lat, radiusM, MAX_PER_TYPE));
        }
        if (requested.contains(TYPE_ACCIDENT_ZONE)) {
            items.addAll(accidentZoneSpatialDao.findNearby(lng, lat, radiusM, MAX_PER_TYPE));
        }

        // 종류별로 따로 조회했으므로 합친 뒤 다시 정렬해야 거리순이 된다
        items.sort(Comparator.comparingInt(PoiItemDTO::getDistanceM));

        return new PoiNearbyResponseDTO(
                new PoiNearbyResponseDTO.Center(lat, lng),
                radiusM,
                items,
                items.size());
    }

    @Override
    public PoiAllResponseDTO findAll() {
        if (allCacheSeconds <= 0) {
            return loadAll();
        }
        PoiAllResponseDTO cached = allCache;
        if (cached != null && isFresh()) {
            return cached;
        }
        // 캐시가 만료된 순간 요청이 몰려도 DB 조회는 한 번만 나가게 한다(뒤늦게 들어온 쪽은 갱신된 것을 쓴다)
        synchronized (this) {
            if (allCache == null || !isFresh()) {
                allCache = loadAll();
                allCacheLoadedNanos = System.nanoTime();
            }
            return allCache;
        }
    }

    private boolean isFresh() {
        return System.nanoTime() - allCacheLoadedNanos < allCacheSeconds * 1_000_000_000L;
    }

    private PoiAllResponseDTO loadAll() {
        // 종류 순서를 고정한다. 응답 본문이 실행마다 같아야 ETag가 의미가 있다.
        List<PoiItemDTO> items = new ArrayList<>();
        items.addAll(poiSpatialDao.findAllRouteFacilities());
        items.addAll(poiSpatialDao.findAllRepairShops());
        items.addAll(poiSpatialDao.findAllBikeStations());
        items.addAll(accidentZoneSpatialDao.findAll());

        if (items.size() > ALL_ITEMS_WARN_THRESHOLD) {
            log.warn("POI 전체 조회가 {}건이다(경고 기준 {}건). 모든 사용자의 다운로드가 이만큼 커졌다 - "
                            + "응답 슬림화나 정적 파일 배포를 검토할 때다",
                    items.size(), ALL_ITEMS_WARN_THRESHOLD);
        }
        return new PoiAllResponseDTO(items, items.size());
    }
}
