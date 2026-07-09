package kr.ridely.dto.tour;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 관광 콘텐츠 응답. (★ 관광데이터 공모전 핵심 데이터)
 *
 * 아래 두 API에서 함께 사용한다:
 *   - GET /api/v1/tours/nearby        (내 주변 관광지 목록)
 *   - GET /api/v1/tours/{contentId}   (관광지 상세)
 *
 * 대응 테이블: tour_attraction
 * 원본 출처  : 한국관광공사 국문 관광정보 서비스 (TourAPI)
 *
 * ※ API마다 채워지는 필드가 다르다:
 *   - 목록 조회: overview(개요)는 무거워서 null. 대신 distanceM(거리)이 채워진다.
 *   - 상세 조회: overview가 채워진다. 대신 distanceM은 null.
 *
 * 응답 예시 (목록 조회):
 * {
 *   "tourAttractionId": 12,
 *   "contentId": "126508",
 *   "contentTypeId": "12",
 *   "title": "선유도공원",
 *   "lat": 37.5434, "lng": 126.8997,
 *   "addr1": "서울특별시 영등포구 선유로 343",
 *   "firstImageUrl": "http://tong.visitkorea.or.kr/...jpg",
 *   "distanceM": 320,
 *   "overview": null
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourAttractionDTO {

    /** 우리 DB의 고유 번호 (tour_attraction.tour_attraction_id) */
    private Long tourAttractionId;

    /**
     * TourAPI가 부여한 콘텐츠 고유 ID.
     * 숫자처럼 보이지만 문자열 (앞자리 0이 있을 수 있음).
     * 상세 조회 URL에 사용: GET /tours/{contentId}
     */
    private String contentId;

    /**
     * 콘텐츠 종류 코드.
     * 12=관광지, 14=문화시설, 15=축제/공연/행사, 25=여행코스,
     * 28=레포츠, 32=숙박, 38=쇼핑, 39=음식점
     *
     * MVP에서는 12·14·39를 주로 해보고, 축제(15)는 확장 대상.
     */
    private String contentTypeId;

    /** 관광지 이름 */
    private String title;

    /** 위도 (tour_attraction.geom의 Y좌표, TourAPI의 mapy) */
    private double lat;

    /** 경도 (tour_attraction.geom의 X좌표, TourAPI의 mapx) */
    private double lng;

    /** 기본 주소 */
    private String addr1;

    /** 상세 주소 (동/호수 등). 없으면 null */
    private String addr2;

    /** 전화번호. 없으면 null */
    private String tel;

    /** 대표 이미지 URL. 없으면 null (이미지 없는 콘텐츠도 많다) */
    private String firstImageUrl;

    /** 썸네일 이미지 URL (목록에서 작게 띄울 때 사용). 없으면 null */
    private String thumbnailUrl;

    /**
     * 관광지 개요 설명.
     * 목록 조회에서는 응답이 무거워지므로 담지 않는다 (null).
     * 상세 조회(/tours/{contentId})에서만 채워진다.
     */
    private String overview;

    /**
     * 행사 시작일. 축제(contentTypeId=15)에만 값이 있다.
     * 그 외 콘텐츠는 null.
     */
    private LocalDate eventStartDate;

    /**
     * 행사 종료일. 축제(contentTypeId=15)에만 값이 있다.
     * 오늘 날짜가 시작~종료 사이인 축제만 코스에 추천한다.
     */
    private LocalDate eventEndDate;

    /**
     * 요청한 위치로부터 떨어진 거리(미터).
     * 위치 기반 목록 조회(/tours/nearby)에서만 채워진다.
     * 상세 조회에서는 null.
     */
    private Integer distanceM;
}
