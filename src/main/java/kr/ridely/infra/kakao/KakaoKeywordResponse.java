package kr.ridely.infra.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Kakao Local 키워드 검색 응답. {@code GET /v2/local/search/keyword.json}
 *
 * 쓰는 필드만 선언하고 나머지는 무시한다. 응답에 {@code category_group_code}·{@code distance} 등이 더 오지만 출발지 선택 화면에 필요한 것은 이름과 좌표, 주소뿐이다.
 *
 * ⚠️ <b>좌표가 문자열로 온다.</b> {@code "x": "126.93390"} 형태다. 숫자로 선언해도 Jackson이 변환해 주지만, 명세에 문자열로 적혀 있어 그대로 받고 서비스에서 바꾼다 - 빈 문자열이나 형식이 다른 값이 왔을 때 파싱 실패 지점이 분명해진다.
 *
 * ⚠️ <b>{@code x}가 경도, {@code y}가 위도다.</b> 우리 코드 대부분이 (lat, lng) 순서인 것과 반대라 바꿔 담을 때 뒤집기 쉽다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordResponse(
        List<Document> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("address_name") String addressName,
            String x,
            String y
    ) {

        /**
         * 화면에 보여줄 주소.
         *
         * 도로명 주소를 우선하되 없으면 지번으로 내린다. 공원·한강 둔치처럼 건물이 없는 곳은 도로명이 비어 오는 경우가 있고, 우리 사용처가 라이딩 출발지라 그런 장소가 오히려 흔하다.
         */
        public String displayAddress() {
            return (roadAddressName == null || roadAddressName.isBlank())
                    ? addressName
                    : roadAddressName;
        }
    }
}
