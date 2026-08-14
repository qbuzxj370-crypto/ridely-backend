package kr.ridely.infra.ors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * openrouteservice 경로 탐색 응답 (GeoJSON 형식).
 *
 * POST /v2/directions/{profile}/geojson 의 응답이다. 구조는 GeoJSON FeatureCollection이고 우리가 쓰는 값은 features[0] 하나에 다 들어 있다.
 *
 *   { "type": "FeatureCollection",
 *     "features": [ { "geometry": { "type":"LineString", "coordinates":[[lng,lat,ele], ...] },
 *                     "properties": { "summary": {"distance": 4822.9, "duration": 1301.5},
 *                                     "ascent": 137.9, "descent": 126.6 } } ] }
 *
 * ⚠️ 좌표는 [경도, 위도] 순이다. PostGIS ST_MakePoint와 같은 순서라 헷갈리진 않지만, 우리 DTO가 대체로 lat을 먼저 두므로 옮길 때 뒤집지 않도록 주의한다.
 *
 * ⚠️ elevation=true로 부르면 좌표가 3개 값([lng, lat, 고도])으로 온다. recommended_route.route_geom은 2D LineString이라 이대로 넣으면 "Geometry has Z dimension but column does not"로 깨진다. 적재 시 ST_Force2D로 떨군다.
 *
 * ⚠️ 이 클래스는 외부 API의 응답 형태일 뿐 우리 API 계약이 아니므로 dto가 아닌 infra/ors에 둔다. TaasFrequentZoneResponse와 같은 기준이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrsDirectionsResponse {

    @JsonProperty("features")
    private List<Feature> features;

    /** 응답이 비정상 구조여도 NPE 없이 접근하기 위한 안전 메서드 */
    public Feature firstFeature() {
        if (features == null || features.isEmpty()) {
            return null;
        }
        return features.get(0);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {

        @JsonProperty("geometry")
        private Geometry geometry;

        @JsonProperty("properties")
        private Properties properties;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {

        /** 항상 "LineString"이다 */
        @JsonProperty("type")
        private String type;

        /** [경도, 위도] 또는 elevation=true면 [경도, 위도, 고도] */
        @JsonProperty("coordinates")
        private List<List<Double>> coordinates;

        public List<List<Double>> coordinatesOrEmpty() {
            return coordinates == null ? Collections.emptyList() : coordinates;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {

        @JsonProperty("summary")
        private Summary summary;

        /**
         * 누적 오르막(m).
         *
         * ⚠️ SRTM 노이즈로 과대 계상된다. 실측에서 고도 범위가 1~32m인 한강 평지 코스에 ascent 137.9m가 나왔다 — docs/shared/SCHEMA_CHANGE_POI.md 6.5. 그래서 강도 판정에는 쓰지 않고 표시용으로만 저장한다.
         */
        @JsonProperty("ascent")
        private Double ascent;

        /** 누적 내리막(m). ascent와 같은 이유로 표시용이다 */
        @JsonProperty("descent")
        private Double descent;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {

        /** 총 거리(m) */
        @JsonProperty("distance")
        private Double distance;

        /** 예상 소요 시간(초). ORS의 자전거 속도 모델 기준이다 */
        @JsonProperty("duration")
        private Double duration;
    }
}
