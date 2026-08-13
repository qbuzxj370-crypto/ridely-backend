package kr.ridely.service;

import kr.ridely.dao.NationalBikeRouteDao;
import kr.ridely.dto.poi.BikeRouteIngestResultDTO;
import kr.ridely.infra.seed.BikeRouteCsvReader;
import kr.ridely.infra.seed.BikeRouteCsvReader.Coordinate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class BikeRouteIngestServiceImpl implements BikeRouteIngestService {

    private static final Logger log = LoggerFactory.getLogger(BikeRouteIngestServiceImpl.class);

    /** LineString은 점이 두 개 이상이어야 만들 수 있다 */
    private static final int MIN_POINTS_FOR_LINE = 2;

    /**
     * 파트 분리 임계값 (m).
     *
     * 원본 CSV는 노선이 여러 갈래로 나뉘는 지점을 표시하지 않는다. 좌표를 순서대로 모두 이으면
     * 실재하지 않는 직선이 13개 노선 합계 726.7km 생긴다(동해안 강원에서만 241.7km).
     * 좌표 간격이 이 값을 넘으면 갈래가 바뀐 것으로 보고 파트를 나눈다.
     *
     * 3km인 근거: 전체 49,244개 간격의 중앙값이 25m, p99.9가 675m다. 4.5km를 좌표 없이
     * 건너뛰는 실제 경로는 어느 노선에도 없다. 3km 초과와 4km 초과의 개수가 20개로 같아
     * 그 사이가 안정 지대다. 상세·교차검증은 docs/shared/SCHEMA_CHANGE_ROUTE_GEOM.md 4장.
     */
    private static final double PART_SPLIT_THRESHOLD_M = 3000.0;

    /** 지구 평균 반지름 (m) */
    private static final double EARTH_RADIUS_M = 6371008.8;

    /**
     * 노선 코드 → 노선 정보.
     *
     * 노선명은 원본 ZIP의 "노선정보 코드북"을, 구간 설명과 거리는 자전거행복나눔(bike.go.kr)
     * 공식 안내를 옮긴 것이다. CSV에는 코드(1~13)만 들어 있어 이 표가 필요하다.
     *
     * 남한강자전거길(3)과 제주환상자전거길(13)은 공식 설명에 구간 표기가 없어 null로 둔다.
     * 제주환상은 순환 노선이라 시작·끝 구분 자체가 없다.
     *
     * ⚠️ 거리를 합산하면 안 된다. 공식 정의상 한강종주 192km가 남한강 132km를 포함한다.
     * 남한강 132km는 팔당대교~충주댐 기준이다(탄금대 기준은 139km).
     */
    private static final Map<Integer, RouteMeta> ROUTE_META = Map.ofEntries(
            Map.entry(1, new RouteMeta("아라자전거길", "아라서해갑문", "아라한강갑문", 21)),
            Map.entry(2, new RouteMeta("한강종주자전거길", "아라한강갑문", "충주댐", 192)),
            Map.entry(3, new RouteMeta("남한강자전거길", null, null, 132)),
            Map.entry(4, new RouteMeta("새재자전거길", "충주탄금대", "상주 상풍교", 100)),
            Map.entry(5, new RouteMeta("낙동강자전거길", "상주 상풍교", "낙동강 하구둑", 385)),
            Map.entry(6, new RouteMeta("금강자전거길", "대청댐", "금강 하구둑", 146)),
            Map.entry(7, new RouteMeta("영산강자전거길", "담양댐", "영산강 하구둑", 133)),
            Map.entry(8, new RouteMeta("북한강자전거길", "밝은 광장", "춘천 신매대교", 70)),
            Map.entry(9, new RouteMeta("섬진강자전거길", "전북임실 섬진강 생활체육공원", "전남광양 배알도수변공원", 149)),
            Map.entry(10, new RouteMeta("오천자전거길", "행촌교차로", "합강공원", 105)),
            Map.entry(11, new RouteMeta("동해안(강원)자전거길", "고성 통일전망대", "삼척 고포마을", 242)),
            Map.entry(12, new RouteMeta("동해안(경북)자전거길", "울진 은어다리", "영덕 해맞이공원", 76)),
            Map.entry(13, new RouteMeta("제주환상자전거길", null, null, 234))
    );

    private final BikeRouteCsvReader csvReader;
    private final NationalBikeRouteDao nationalBikeRouteDao;

    public BikeRouteIngestServiceImpl(BikeRouteCsvReader csvReader,
                                      NationalBikeRouteDao nationalBikeRouteDao) {
        this.csvReader = csvReader;
        this.nationalBikeRouteDao = nationalBikeRouteDao;
    }

    /**
     * 노선 13개를 한 트랜잭션으로 적재한다.
     * 일부만 들어간 상태를 남기지 않기 위해서다. 13행이라 트랜잭션이 길어질 걱정은 없다.
     */
    @Override
    @Transactional
    public BikeRouteIngestResultDTO ingestNationalRoutes() {
        BikeRouteCsvReader.ParseResult parsed = csvReader.readRouteCoordinates();

        List<BikeRouteIngestResultDTO.RouteSummary> summaries = new ArrayList<>();
        int totalPointCount = 0;

        for (Map.Entry<Integer, List<Coordinate>> entry : parsed.getCoordinatesByRoute().entrySet()) {
            int routeCode = entry.getKey();
            List<Coordinate> points = entry.getValue();

            RouteMeta meta = ROUTE_META.get(routeCode);
            if (meta == null) {
                // 코드북에 없는 코드. 리더가 1~13만 넘기므로 정상 흐름에선 발생하지 않는다
                log.warn("코드북에 없는 노선 코드라 건너뛴다: {}", routeCode);
                continue;
            }

            List<List<Coordinate>> parts = splitByGap(points);
            if (parts.isEmpty()) {
                log.warn("선을 만들 수 있는 파트가 없다: {} ({}점)", meta.getRouteName(), points.size());
                continue;
            }

            BigDecimal officialLengthKm = BigDecimal.valueOf(meta.getOfficialLengthKm());
            BigDecimal geometryLengthKm = nationalBikeRouteDao.upsert(
                    meta.getRouteName(), meta.getStartDesc(), meta.getEndDesc(),
                    officialLengthKm, toMultiLineStringWkt(parts));

            int usedPoints = parts.stream().mapToInt(List::size).sum();
            totalPointCount += usedPoints;
            summaries.add(new BikeRouteIngestResultDTO.RouteSummary(
                    routeCode, meta.getRouteName(), usedPoints, parts.size(),
                    officialLengthKm, geometryLengthKm));

            log.debug("노선 적재: {} ({}점, {}파트, 실측 {}km / 공식 {}km)",
                    meta.getRouteName(), usedPoints, parts.size(), geometryLengthKm, officialLengthKm);
        }

        log.info("국토종주 자전거길 적재 완료: {}개 노선, {}점 (인코딩 {}, 건너뜀 {}행)",
                summaries.size(), totalPointCount, parsed.getCharsetName(), parsed.getSkippedRowCount());

        return new BikeRouteIngestResultDTO(
                parsed.getCharsetName(),
                parsed.getDataRowCount(),
                parsed.getSkippedRowCount(),
                parsed.getCoordinatesByRoute().size(),
                totalPointCount,
                summaries.size(),
                summaries);
    }

    /**
     * 좌표 간격이 임계값을 넘는 지점에서 파트를 나눈다.
     *
     * 점이 하나뿐인 파트는 선을 만들 수 없으므로 버린다. 원본에서 관측되지는 않았지만
     * 임계값을 낮추거나 파일이 바뀌면 생길 수 있다.
     *
     * @return 점 2개 이상인 파트만. 하나도 없으면 빈 목록
     */
    private List<List<Coordinate>> splitByGap(List<Coordinate> points) {
        List<List<Coordinate>> parts = new ArrayList<>();
        if (points.isEmpty()) {
            return parts;
        }
        List<Coordinate> current = new ArrayList<>();
        current.add(points.get(0));

        for (int i = 1; i < points.size(); i++) {
            if (distanceM(points.get(i - 1), points.get(i)) > PART_SPLIT_THRESHOLD_M) {
                parts.add(current);
                current = new ArrayList<>();
            }
            current.add(points.get(i));
        }
        parts.add(current);

        List<List<Coordinate>> usable = new ArrayList<>(parts.size());
        for (List<Coordinate> part : parts) {
            if (part.size() >= MIN_POINTS_FOR_LINE) {
                usable.add(part);
            } else {
                log.debug("점이 부족한 파트를 버린다 ({}점)", part.size());
            }
        }
        return usable;
    }

    /**
     * 파트 목록을 WKT MultiLineString 문자열로 만든다.
     *
     * 좌표가 노선당 최대 8천여 개라 SQL에 직접 조립하지 않고 파라미터로 넘긴다.
     * WKT는 "경도 위도" 순서이며, 리더가 이미 그 순서로 읽어 둔다.
     */
    private String toMultiLineStringWkt(List<List<Coordinate>> parts) {
        StringJoiner partJoiner = new StringJoiner(",", "MULTILINESTRING(", ")");
        for (List<Coordinate> part : parts) {
            StringJoiner pointJoiner = new StringJoiner(",", "(", ")");
            for (Coordinate point : part) {
                pointJoiner.add(point.getLng() + " " + point.getLat());
            }
            partJoiner.add(pointJoiner.toString());
        }
        return partJoiner.toString();
    }

    /**
     * 두 좌표 사이 대권 거리 (m).
     *
     * PostGIS를 거치지 않고 자바에서 재는 이유: 파트를 나눈 뒤에야 WKT를 만들 수 있어
     * DB에 넣기 전에 판정이 끝나야 한다. 분석에 쓴 것과 같은 식(R=6371008.8m)이라
     * SCHEMA_CHANGE_ROUTE_GEOM.md의 수치와 직접 대조된다.
     */
    private double distanceM(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.getLat());
        double lat2 = Math.toRadians(b.getLat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.getLng() - a.getLng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    /** 코드북·공식 안내에서 옮긴 노선 정보 */
    @Getter
    @AllArgsConstructor
    private static class RouteMeta {
        private final String routeName;
        private final String startDesc;
        private final String endDesc;
        /** 자전거행복나눔 공식 안내 거리 (km) */
        private final int officialLengthKm;
    }
}
