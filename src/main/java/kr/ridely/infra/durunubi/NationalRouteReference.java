package kr.ridely.infra.durunubi;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 국토종주 자전거길 13개의 대조 기준표 (스파이크 전용).
 *
 * <p>두루누비 응답을 <b>무엇과 비교할 것인가</b>를 담는다. 두 종류의 기준이 들어 있다.
 *
 * <ol>
 *   <li><b>이름 매칭 토큰</b> — 두루누비의 길 이름이 행안부 코드북과 다를 수 있어
 *       (예: "한강종주자전거길" vs "한강 자전거길") 완전 일치로는 못 찾는다.
 *       포함해야 할 토큰과 포함되면 안 되는 토큰으로 판정한다.
 *       "한강"만으로 찾으면 남한강·북한강이 함께 걸리므로 제외 토큰이 필요하다.</li>
 *   <li><b>CSV 분석 결과</b> — 행안부 CSV를 임계값 3km로 분리해 얻은 구간 수·길이.
 *       GPX의 trkseg 개수가 이 구간 수와 맞으면 <b>휴리스틱이 원본과 같은 답을 낸 것</b>이고,
 *       그때부터는 휴리스틱을 버리고 GPX를 정본으로 쓸 수 있다.</li>
 * </ol>
 *
 * <p>값을 코드에 박아둔 이유: 스파이크 판정에만 쓰는 일회성 대조표이고,
 * 원본 CSV가 없는 환경에서도 리포트가 나와야 하기 때문이다.
 * 채택이 확정되면 이 클래스는 삭제된다.
 *
 * <p>공식 거리 출처: 자전거행복나눔(bike.go.kr) 노선 안내.
 * 남한강 132km는 팔당대교~충주댐 기준이다(탄금대 기준은 139km).
 */
public enum NationalRouteReference {

    ARA(1, "아라자전거길", List.of("아라"), List.of(), 1, 20.6, 21),
    HANGANG(2, "한강종주자전거길", List.of("한강"), List.of("남한강", "북한강"), 3, 94.6, 192),
    NAMHANGANG(3, "남한강자전거길", List.of("남한강"), List.of(), 9, 131.3, 132),
    SAEJAE(4, "새재자전거길", List.of("새재"), List.of(), 1, 99.1, 100),
    NAKDONG(5, "낙동강자전거길", List.of("낙동강"), List.of(), 1, 366.3, 385),
    GEUM(6, "금강자전거길", List.of("금강"), List.of(), 1, 143.5, 146),
    YEONGSAN(7, "영산강자전거길", List.of("영산강"), List.of(), 1, 129.0, 133),
    BUKHANGANG(8, "북한강자전거길", List.of("북한강"), List.of(), 5, 112.9, 70),
    SEOMJIN(9, "섬진강자전거길", List.of("섬진강"), List.of(), 1, 151.6, 149),
    OCHEON(10, "오천자전거길", List.of("오천"), List.of(), 4, 102.3, 105),
    DONGHAE_GW(11, "동해안(강원)자전거길", List.of("동해안", "강원"), List.of(), 4, 243.2, 242),
    DONGHAE_GB(12, "동해안(경북)자전거길", List.of("동해안", "경북"), List.of(), 1, 123.5, 76),
    JEJU(13, "제주환상자전거길", List.of("제주"), List.of(), 1, 232.7, 234);

    private final int routeCode;
    private final String officialName;
    private final List<String> requiredTokens;
    private final List<String> excludedTokens;
    private final int csvPartsAt3km;
    private final double csvLengthKm;
    private final int officialLengthKm;

    NationalRouteReference(int routeCode, String officialName,
                           List<String> requiredTokens, List<String> excludedTokens,
                           int csvPartsAt3km, double csvLengthKm, int officialLengthKm) {
        this.routeCode = routeCode;
        this.officialName = officialName;
        this.requiredTokens = requiredTokens;
        this.excludedTokens = excludedTokens;
        this.csvPartsAt3km = csvPartsAt3km;
        this.csvLengthKm = csvLengthKm;
        this.officialLengthKm = officialLengthKm;
    }

    /**
     * 두루누비의 길 이름이 이 노선에 해당하는지 판정한다.
     * 공백을 제거하고 비교한다 — "한강 자전거길"과 "한강자전거길"을 같게 보기 위해서다.
     */
    public boolean matches(String themeName) {
        if (themeName == null || themeName.isBlank()) {
            return false;
        }
        String normalized = themeName.replaceAll("\\s+", "");
        for (String excluded : excludedTokens) {
            if (normalized.contains(excluded)) {
                return false;
            }
        }
        for (String required : requiredTokens) {
            if (!normalized.contains(required)) {
                return false;
            }
        }
        return true;
    }

    /** 이름으로 해당하는 국토종주 노선을 찾는다. 없으면 empty */
    public static Optional<NationalRouteReference> find(String themeName) {
        return Arrays.stream(values()).filter(r -> r.matches(themeName)).findFirst();
    }

    /**
     * 노선 코드(1~13)로 찾는다.
     * ordinal에 기대지 않는다 — 상수 순서를 바꾸면 조용히 어긋나기 때문이다.
     */
    public static Optional<NationalRouteReference> findByCode(int routeCode) {
        return Arrays.stream(values()).filter(r -> r.routeCode == routeCode).findFirst();
    }

    public int getRouteCode() {
        return routeCode;
    }

    public String getOfficialName() {
        return officialName;
    }

    public int getCsvPartsAt3km() {
        return csvPartsAt3km;
    }

    public double getCsvLengthKm() {
        return csvLengthKm;
    }

    public int getOfficialLengthKm() {
        return officialLengthKm;
    }
}
