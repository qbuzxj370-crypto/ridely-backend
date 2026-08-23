package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.GeoDistance;
import kr.ridely.common.ErrorCode;
import kr.ridely.config.MvpAreaProperties;
import kr.ridely.config.RouteProperties;
import kr.ridely.dao.AccidentZoneSpatialDao;
import kr.ridely.dao.NationalBikeRouteDao;
import kr.ridely.dao.RouteDao;
import kr.ridely.dao.UserSettingsDao;
import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;
import kr.ridely.dto.route.WaypointDTO;
import kr.ridely.infra.llm.CoachCommentClient;
import kr.ridely.infra.llm.CourseDesignClient;
import kr.ridely.infra.llm.LlmProperties;
import kr.ridely.infra.ors.OrsClient;
import kr.ridely.infra.ors.OrsRouteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RouteRecommendServiceImpl implements RouteRecommendService {

    private static final Logger log = LoggerFactory.getLogger(RouteRecommendServiceImpl.class);

    /** 우선순위 기본값. 요청에서 생략하면 이 값을 쓴다 */
    private static final BigDecimal DEFAULT_CONVENIENCE = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_EXERCISE = new BigDecimal("0.30");
    private static final BigDecimal DEFAULT_SCENERY = new BigDecimal("0.20");

    /**
     * 우선순위 합 허용 오차.
     *
     * 클라이언트가 0.33 + 0.33 + 0.34처럼 반올림한 값을 보내는 것을 막지 않으려는 여유다. 0.01이면 사람이 손으로 채운 값은 통과하고 명백히 잘못된 조합은 걸린다.
     */
    private static final BigDecimal PRIORITY_SUM = BigDecimal.ONE;
    private static final BigDecimal PRIORITY_TOLERANCE = new BigDecimal("0.01");

    /**
     * 목표 거리는 최소한 직선거리보다 길어야 한다.
     *
     * 8km 떨어진 두 지점을 5km로 달릴 수는 없다. 이걸 막지 않으면 후보 수집에서 여유 거리가 음수가 되고, 반경이 하한으로 떨어지면서 코스가 이상해진다.
     */
    private static final double MIN_SLACK_RATIO = 1.0;

    /**
     * 목표 거리 허용 하한. 실측이 목표의 이 비율에 못 미치면 경로를 늘린다.
     *
     * ±10%는 RouteRecommendRequestDTO가 선언한 목표치다.
     *
     * 상한은 보지 않는다. 초과가 안 나서가 아니라, 초과는 여기서 고칠 수 있는 문제가 아니라서다. 목표 5km로 실측하면 6.1km가 나오는데(+22%) 이는 직선거리 3.8km 구간을 자전거도로로 따라가면 이미 5km에 가깝고 거기에 경유지 우회가 붙기 때문이다. 경로를 줄이려면 경유지를 덜어내야 하고 그건 설계 단계의 일이다. 짧은 목표를 아예 거부하는 방법도 있지만(verifyReachable의 MIN_SLACK_RATIO 상향) 그건 요청을 못 받는 것이지 맞추는 것이 아니다.
     */
    private static final double DISTANCE_TOLERANCE = 0.9;

    /**
     * 연장점까지의 거리를 부족분의 몇 분의 일로 잡을지.
     *
     * 출발지 반대편으로 나갔다가 되돌아오므로 실제 늘어나는 거리는 연장점까지 거리의 두 배다. 부족분이 4km면 2km 지점을 잡는다.
     */
    private static final int EXTENSION_ROUNDTRIP = 2;

    private static final double KM_TO_M = 1000.0;

    private final InfraCandidateCollector candidateCollector;
    private final CourseDesignClient courseDesignClient;
    private final CoachCommentClient coachCommentClient;
    private final OrsClient orsClient;
    private final IntensityCalculator intensityCalculator;
    private final AscentEstimator ascentEstimator;
    private final MvpAreaProperties mvpArea;
    private final RouteDao routeDao;
    private final NationalBikeRouteDao nationalBikeRouteDao;
    private final AccidentZoneSpatialDao accidentZoneSpatialDao;
    private final UserSettingsDao userSettingsDao;
    private final RouteProperties routeProperties;
    private final LlmProperties llmProperties;

    public RouteRecommendServiceImpl(InfraCandidateCollector candidateCollector,
                                     CourseDesignClient courseDesignClient,
                                     CoachCommentClient coachCommentClient,
                                     OrsClient orsClient,
                                     IntensityCalculator intensityCalculator,
                                     AscentEstimator ascentEstimator,
                                     MvpAreaProperties mvpArea,
                                     RouteDao routeDao,
                                     NationalBikeRouteDao nationalBikeRouteDao,
                                     AccidentZoneSpatialDao accidentZoneSpatialDao,
                                     UserSettingsDao userSettingsDao,
                                     RouteProperties routeProperties,
                                     LlmProperties llmProperties) {
        this.routeProperties = routeProperties;
        this.nationalBikeRouteDao = nationalBikeRouteDao;
        this.accidentZoneSpatialDao = accidentZoneSpatialDao;
        this.userSettingsDao = userSettingsDao;
        this.candidateCollector = candidateCollector;
        this.courseDesignClient = courseDesignClient;
        this.coachCommentClient = coachCommentClient;
        this.orsClient = orsClient;
        this.intensityCalculator = intensityCalculator;
        this.ascentEstimator = ascentEstimator;
        this.mvpArea = mvpArea;
        this.routeDao = routeDao;
        this.llmProperties = llmProperties;
    }

    @Override
    public RouteRecommendResponseDTO recommend(RouteRecommendRequestDTO request, Long userId,
                                               Boolean avoidHeader) {
        long startedAt = System.currentTimeMillis();

        BigDecimal convenience = orDefault(request.getPriorityConvenience(), DEFAULT_CONVENIENCE);
        BigDecimal exercise = orDefault(request.getPriorityExercise(), DEFAULT_EXERCISE);
        BigDecimal scenery = orDefault(request.getPriorityScenery(), DEFAULT_SCENERY);

        request.setPriorityConvenience(convenience);
        request.setPriorityExercise(exercise);
        request.setPriorityScenery(scenery);

        double startLng = request.getStartLng();
        double startLat = request.getStartLat();
        Double endLng = request.getEndLng();
        Double endLat = request.getEndLat();
        double targetDistanceKm = request.getTargetDistanceKm().doubleValue();

        verifyPrioritySum(convenience, exercise, scenery);
        verifyInServiceArea(startLng, startLat, endLng, endLat);
        verifyReachable(startLng, startLat, endLng, endLat, targetDistanceKm);

        boolean circular = endLng == null || endLat == null;
        double straightLineKm = circular ? 0
                : GeoDistance.haversineM(startLng, startLat, endLng, endLat) / KM_TO_M;

        RouteCandidatesDTO candidates = candidateCollector.collect(
                startLng, startLat, endLng, endLat, targetDistanceKm);
        if (candidates.isEmpty()) {
            log.error("후보가 한 건도 없다. 서비스 지역 안이지만 주변 데이터가 비어 있다");
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        CourseDesignDTO design = courseDesignClient.design(
                candidates, targetDistanceKm, circular, straightLineKm,
                convenience, exercise, scenery);
        List<CandidateDTO> waypoints = resolveWaypoints(design, candidates);

        String avoidGeometry = resolveAvoidGeometry(userId, avoidHeader);
        Routed routed = routeWithAvoidFallback(
                avoidGeometry, targetDistanceKm, startLng, startLat, endLng, endLat, waypoints);
        OrsRouteResult route = routed.route();

        // 거리 보정이 끝난 최종 형상으로 판정한다. 보정 전에 하면 연장 구간에서
        // 새로 지나가게 된 구역을 통째로 놓친다
        List<PassingDangerZoneDTO> dangerZones =
                accidentZoneSpatialDao.findPassing(route.getGeometryGeoJson());

        String intensityLevel = intensityCalculator.calculate(route.distanceKm());
        CoachCommentDTO comment = coachCommentClient.generate(
                design, candidates, targetDistanceKm, circular,
                route.distanceKm(), route.durationMin(), intensityLevel,
                dangerZones, routed.avoidApplied());

        log.info("코스 추천 완료: 목표 {}km → 실측 {}km, 경유지 {}곳, 사고다발지 {}곳(회피 {}), {}, 총 {}ms",
                targetDistanceKm, route.distanceKm(), waypoints.size(), dangerZones.size(),
                routed.avoidApplied() ? "적용" : "미적용",
                intensityLevel, System.currentTimeMillis() - startedAt);

        RouteRecommendResponseDTO response = assemble(
                route, waypoints, design, comment, intensityLevel, dangerZones, routed.avoidApplied());

        RouteDao.Saved saved = routeDao.insert(request, response, userId, llmProperties.primaryProvider());
        response.setRecommendedRouteId(saved.recommendedRouteId());
        response.setCreatedAt(saved.createdAt());
        // 저장된 형상으로 바꿔 넣는다. ORS 원본은 3차원이라 그대로 두면
        // POST 응답과 GET 재조회 결과의 좌표 차원이 달라진다
        response.setRouteGeoJson(saved.routeGeoJson());
        return response;
    }

    @Override
    public RouteRecommendResponseDTO findById(long recommendedRouteId) {
        return routeDao.selectById(recommendedRouteId)
                .orElseThrow(() -> {
                    log.warn("추천 코스를 찾지 못했다: {}", recommendedRouteId);
                    return new BusinessException(ErrorCode.COMMON_004);
                });
    }

    /**
     * LLM이 고른 경유지 중 실재하는 것만 남긴다.
     *
     * 없는 번호를 만들어내는 경우가 있어 후보 목록과 대조한다. 지금은 걸러내기만 하고 재호출은 하지 않는다.
     *
     * 하나도 안 남아도 실패시키지 않는다. 출발지와 도착지만으로도 경로는 그려지고, 경유지 없는 코스가 추천 실패보다는 낫다. 다만 순환 코스는 출발지와 도착지가 같아 거리가 0이 되므로 ORS 쪽에서 걸린다.
     */
    private List<CandidateDTO> resolveWaypoints(CourseDesignDTO design, RouteCandidatesDTO candidates) {
        List<CandidateDTO> resolved = new ArrayList<>();
        for (CourseDesignDTO.SelectedWaypoint selected : design.waypointsOrEmpty()) {
            Optional<CandidateDTO> found = candidates.find(selected.getType(), selected.getId());
            if (found.isEmpty()) {
                log.warn("실재하지 않는 경유지를 걸렀다: type={} id={}",
                        selected.getType(), selected.getId());
                continue;
            }
            resolved.add(found.get());
        }
        if (resolved.isEmpty()) {
            log.warn("남은 경유지가 없다. 출발지와 도착지만으로 경로를 그린다");
        }
        return resolved;
    }

    /** 경로와 회피 적용 여부. 회피는 실패하면 꺼지므로 결과와 함께 돌려줘야 한다 */
    private record Routed(OrsRouteResult route, boolean avoidApplied) {
    }

    /**
     * 회피를 요청했는지 판정한다.
     *
     * 회원은 저장된 설정을, 비회원은 요청 헤더를 본다. 회원이 헤더를 함께 보내도 설정이 이긴다 — 설정 화면에서 끈 것을 헤더로 되살릴 수 있으면 설정의 의미가 없다.
     */
    private boolean isAvoidRequested(Long userId, Boolean avoidHeader) {
        if (userId == null) {
            return Boolean.TRUE.equals(avoidHeader);
        }
        Boolean saved = userSettingsDao.selectAvoidDangerZones(userId);
        if (saved == null) {
            // 가입 트랜잭션이 기본값 행을 만들므로 정상 회원에게는 없을 수 없다.
            // 없다면 데이터가 어긋난 것이라 조용히 넘기지 않는다
            log.warn("회원 설정 행이 없다: userId={}. 회피를 끈 것으로 본다", userId);
        }
        return Boolean.TRUE.equals(saved);
    }

    /**
     * 회피할 도형을 가져온다.
     *
     * @return 회피할 도형. 회피를 끈 경우나 대상 구역이 없으면 null
     */
    private String resolveAvoidGeometry(Long userId, Boolean avoidHeader) {
        if (!isAvoidRequested(userId, avoidHeader)) {
            return null;
        }
        // 무엇을 피할지가 곧 거리를 조절하는 손잡이다. 전 등급을 피하면 우회가 커져
        // 목표 거리를 크게 넘긴다 — 근거는 application.yml의 avoid-danger-levels 주석
        return accidentZoneSpatialDao.findAvoidGeometry(routeProperties.avoidDangerLevels())
                .orElse(null);
    }

    /**
     * 회피를 적용해 경로를 그리고, 실패하면 회피 없이 다시 그린다.
     *
     * 사고다발지는 교차로에 생기고 그 교차로가 유일한 통로일 수 있다. 그때 라우팅 엔진은 경로를 찾지 못한다. 코스를 아예 못 주는 것보다 회피를 포기하고 주는 편이 낫고, 대신 응답의 avoidDangerZonesApplied로 그 사실을 알린다.
     *
     * 재시도 시 좌표를 새로 만든다. 거리 보정이 연장점을 목록에 끼워 넣기 때문에 실패한 목록을 그대로 다시 쓰면 회피 없이 그리려던 경로에 그 점이 남는다.
     */
    private Routed routeWithAvoidFallback(String avoidGeometry, double targetDistanceKm,
                                          double startLng, double startLat,
                                          Double endLng, Double endLat,
                                          List<CandidateDTO> waypoints) {
        if (avoidGeometry != null) {
            try {
                List<double[]> coordinates =
                        toCoordinates(startLng, startLat, endLng, endLat, waypoints);
                OrsRouteResult route = extendIfShort(
                        orsClient.route(coordinates, avoidGeometry), coordinates, avoidGeometry,
                        targetDistanceKm, startLng, startLat, endLng, endLat);
                return new Routed(route, true);
            } catch (BusinessException e) {
                log.warn("회피 경로를 찾지 못했다. 회피 없이 다시 그린다 — 사고다발지가 유일한 통로일 수 있다");
            }
        }

        List<double[]> coordinates = toCoordinates(startLng, startLat, endLng, endLat, waypoints);
        OrsRouteResult route = extendIfShort(
                orsClient.route(coordinates, null), coordinates, null,
                targetDistanceKm, startLng, startLat, endLng, endLat);
        return new Routed(route, false);
    }

    /**
     * 목표 거리에 못 미치면 경로를 한 번 늘려 다시 잰다.
     *
     * 경유지 선정으로는 거리를 못 맞춘다. 실측 세 번에서 목표의 51~62%에 그쳤고, 후보를 24건에서 86건으로 늘려도 마찬가지였다. 자연스러운 코스를 만드는 것과 목표 거리를 채우는 것이 다른 목표라서, 후보를 아무리 좋게 줘도 LLM은 경로에 붙은 지점을 고른다. 그 판단 자체는 옳다 — 한강 라이딩에 4km 떨어진 대여소를 넣는 코스가 더 나은 코스는 아니다.
     *
     * 그래서 거리는 설계가 아니라 여기서 맞춘다. 부족분의 절반만큼 도착지 반대편으로 나갔다 오는 지점을 경로 맨 앞에 끼운다. 자전거도로 위의 점이라 코스가 도로를 벗어나지 않는다.
     *
     * 보정은 한 번만 한다. 수렴 루프를 만들지 않는 이유는 ORS 호출이 그만큼 늘고, 한 번 보정으로 얼마나 맞는지를 아직 모르기 때문이다. 실측을 쌓아 보고 필요하면 늘린다.
     *
     * 회피 도형은 1차 호출과 같은 값을 그대로 넘긴다. 보정으로 늘어난 구간에서 사고다발지를 다시 밟으면 회피가 무의미해진다.
     *
     * @param coordinates    1차 호출에 쓴 좌표. 보정 시 이 목록에 연장점을 끼워 넣는다
     * @param avoidGeometry  회피할 도형. null이면 회피하지 않는다
     */
    private OrsRouteResult extendIfShort(OrsRouteResult route, List<double[]> coordinates,
                                         String avoidGeometry, double targetDistanceKm,
                                         double startLng, double startLat,
                                         Double endLng, Double endLat) {
        if (route.distanceKm() >= targetDistanceKm * DISTANCE_TOLERANCE) {
            return route;
        }

        int extensionM = (int) Math.round(
                (targetDistanceKm - route.distanceKm()) * KM_TO_M / EXTENSION_ROUNDTRIP);
        boolean circular = endLng == null || endLat == null;

        Optional<double[]> extensionPoint = nationalBikeRouteDao.findExtensionPoint(
                startLng, startLat,
                circular ? startLng : endLng, circular ? startLat : endLat,
                extensionM);
        if (extensionPoint.isEmpty()) {
            log.warn("거리 보정을 건너뛴다: 자전거도로에서 출발지 {}m 지점을 찾지 못했다 (실측 {}km, 목표 {}km)",
                    extensionM, route.distanceKm(), targetDistanceKm);
            return route;
        }

        // 출발 직후에 넣는다. 나갔다 돌아온 뒤 원래 경유지를 설계 순서대로 지난다
        coordinates.add(1, extensionPoint.get());
        OrsRouteResult extended = orsClient.route(coordinates, avoidGeometry);

        // 늘린 결과가 오히려 목표에서 더 멀면 버린다. 연장점 거리는 직선 기준 추정이라
        // 실제 도로를 따라가면 크게 넘길 수 있다
        if (gapTo(targetDistanceKm, extended) >= gapTo(targetDistanceKm, route)) {
            log.warn("거리 보정 결과가 더 나빠 되돌린다: {}km → {}km (목표 {}km)",
                    route.distanceKm(), extended.distanceKm(), targetDistanceKm);
            coordinates.remove(1);
            return route;
        }

        log.info("거리 보정: {}km → {}km (목표 {}km, 연장점 {}m)",
                route.distanceKm(), extended.distanceKm(), targetDistanceKm, extensionM);
        return extended;
    }

    private double gapTo(double targetDistanceKm, OrsRouteResult route) {
        return Math.abs(targetDistanceKm - route.distanceKm());
    }

    /**
     * ORS에 넘길 좌표를 만든다.
     *
     * 출발 → 경유(설계 순서) → 도착 순서다. 도착지가 없는 순환 코스는 출발지를 도착지로 넣는다. 좌표는 [경도, 위도] 순서다.
     */
    private List<double[]> toCoordinates(double startLng, double startLat,
                                         Double endLng, Double endLat,
                                         List<CandidateDTO> waypoints) {
        List<double[]> coordinates = new ArrayList<>();
        coordinates.add(new double[]{startLng, startLat});
        for (CandidateDTO waypoint : waypoints) {
            coordinates.add(new double[]{waypoint.getLng(), waypoint.getLat()});
        }
        boolean circular = endLng == null || endLat == null;
        coordinates.add(circular
                ? new double[]{startLng, startLat}
                : new double[]{endLng, endLat});
        return coordinates;
    }

    private RouteRecommendResponseDTO assemble(OrsRouteResult route, List<CandidateDTO> waypoints,
                                               CourseDesignDTO design, CoachCommentDTO comment,
                                               String intensityLevel,
                                               List<PassingDangerZoneDTO> dangerZones,
                                               boolean avoidApplied) {
        RouteRecommendResponseDTO response = new RouteRecommendResponseDTO();

        // recommendedRouteId와 createdAt은 영속 단계에서 채운다
        response.setTotalDistanceKm(BigDecimal.valueOf(route.distanceKm()));
        response.setEstimatedDurationMin(route.durationMin());
        response.setTotalAscentM((int) Math.round(ascentEstimator.estimate(route)));
        response.setTotalDescentM((int) Math.round(route.getDescentM()));
        response.setIntensityLevel(intensityLevel);
        response.setRouteGeoJson(route.getGeometryGeoJson());
        // 회피를 요청했어도 경로를 못 찾으면 꺼진 채로 나간다. 프론트는 이 값으로
        // "회피된 코스"인지 "지나가지만 알려주는 코스"인지 가른다
        response.setAvoidDangerZonesApplied(avoidApplied);

        response.setWaypoints(toWaypointDtos(waypoints, design, route.distanceKm()));
        response.setPassingDangerZones(dangerZones);

        response.setAiTitle(comment.getTitle());
        response.setAiHighlights(comment.highlightsOrEmpty());
        response.setAiCoachComment(comment.getCoachComment());
        response.setAiDangerZoneAlert(comment.getDangerZoneAlert());
        response.setAiNextStepSuggestion(comment.getNextStepSuggestion());
        return response;
    }

    /**
     * 경유지에 설계 단계의 선정 이유를 붙인다. 순서는 설계가 정한 그대로다.
     *
     * 진행 위치는 목표 거리가 아니라 실측 거리로 환산한다. 목표를 곱하면 경로보다 먼 지점이 나올 수 있다. 실측에서 총 6.9km 코스에 마지막 경유지가 10.7km 지점으로 저장된 적이 있다.
     *
     * 그래도 근사다. 진행도는 직선 축 위의 위치라 실제 경로 위 위치와 다르다. 정확히 하려면 ORS 결과 위로 다시 투영해야 한다.
     */
    private List<WaypointDTO> toWaypointDtos(List<CandidateDTO> waypoints, CourseDesignDTO design,
                                             double totalDistanceKm) {
        List<WaypointDTO> result = new ArrayList<>();
        for (CandidateDTO candidate : waypoints) {
            WaypointDTO dto = new WaypointDTO();
            dto.setType(candidate.getType());
            dto.setId(candidate.getId());
            dto.setName(candidate.getName());
            dto.setLat(candidate.getLat());
            dto.setLng(candidate.getLng());

            Double progressKm = candidate.progressKm(totalDistanceKm);
            if (progressKm != null) {
                dto.setDistanceFromStartKm(BigDecimal.valueOf(progressKm));
            }
            dto.setReason(reasonOf(design, candidate));
            result.add(dto);
        }
        return result;
    }

    private String reasonOf(CourseDesignDTO design, CandidateDTO candidate) {
        return design.waypointsOrEmpty().stream()
                .filter(w -> candidate.matches(w.getType(), w.getId()))
                .map(CourseDesignDTO.SelectedWaypoint::getReason)
                .findFirst()
                .orElse(null);
    }

    /**
     * 우선순위 합이 1인지 본다.
     *
     * 합이 1이 아니면 가중치의 의미가 없어진다. 셋 다 1.0으로 보내면 "전부 최우선"이 되어 LLM이 판단 기준을 잃는다.
     */
    private void verifyPrioritySum(BigDecimal convenience, BigDecimal exercise, BigDecimal scenery) {
        BigDecimal sum = convenience.add(exercise).add(scenery);
        if (sum.subtract(PRIORITY_SUM).abs().compareTo(PRIORITY_TOLERANCE) > 0) {
            log.warn("우선순위 합이 1이 아니다: {} (편의 {} / 운동 {} / 풍경 {})",
                    sum, convenience, exercise, scenery);
            throw new BusinessException(ErrorCode.ROUTE_001);
        }
    }

    /**
     * 출발지와 도착지가 서비스 지역 안인지 본다.
     *
     * 밖이면 후보가 한 건도 안 잡히고, 그 상태로 LLM을 부르면 토큰만 쓰고 실패한다. 외부 호출 전에 막는다.
     */
    private void verifyInServiceArea(double startLng, double startLat, Double endLng, Double endLat) {
        if (!inMvpArea(startLng, startLat)) {
            log.warn("출발지가 서비스 지역 밖이다: {}, {}", startLat, startLng);
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        if (endLng != null && endLat != null && !inMvpArea(endLng, endLat)) {
            log.warn("도착지가 서비스 지역 밖이다: {}, {}", endLat, endLng);
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
    }

    private boolean inMvpArea(double lng, double lat) {
        return lng >= mvpArea.minLng() && lng <= mvpArea.maxLng()
                && lat >= mvpArea.minLat() && lat <= mvpArea.maxLat();
    }

    /**
     * 목표 거리로 도착지까지 갈 수 있는지 본다.
     *
     * 직선거리보다 짧은 목표를 받으면 물리적으로 불가능하다. 걸러내지 않으면 후보 수집의 여유 거리가 음수가 되어 반경이 하한으로 떨어지고, 코스가 조용히 이상해진다.
     */
    private void verifyReachable(double startLng, double startLat,
                                 Double endLng, Double endLat, double targetDistanceKm) {
        if (endLng == null || endLat == null) {
            return;
        }
        double straightKm = GeoDistance.haversineM(startLng, startLat, endLng, endLat) / KM_TO_M;
        if (targetDistanceKm < straightKm * MIN_SLACK_RATIO) {
            log.warn("목표 거리가 직선거리보다 짧다: 목표 {}km, 직선 {}km",
                    targetDistanceKm, Math.round(straightKm * 10) / 10.0);
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
    }

    private BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }
}
