package kr.ridely.infra.taas;

import java.util.Arrays;
import java.util.List;

/**
 * TAAS 시군구코드 — 서울특별시 25개 자치구.
 *
 * 출처: 한국도로교통공단 Open API 활용가이드 v1.1 「3.3 guGun 요청값」.
 * 법정동 코드의 시군구 3자리다(강남구 11680 → siDo=11, guGun=680).
 *
 * 이 표가 필요한 이유: {@code guGun}이 필수 파라미터라서 시도 단위로는 조회할 수 없다.
 * 실호출에서도 {@code guGun}을 빼면 {@code INVALID_REQUEST_PARAMETER_ERROR}(코드 10)가 돌아온다.
 * 서울 전체를 받으려면 25개 구를 각각 호출해야 한다.
 *
 * MVP 서비스 범위가 한강 서울 구간이라 서울만 담는다.
 * 지역을 넓히면 활용가이드 3.3에서 해당 시도의 구군 코드를 추가한다.
 */
public enum SeoulDistrict {

    JONGNO("110", "종로구"),
    JUNG("140", "중구"),
    YONGSAN("170", "용산구"),
    SEONGDONG("200", "성동구"),
    GWANGJIN("215", "광진구"),
    DONGDAEMUN("230", "동대문구"),
    JUNGNANG("260", "중랑구"),
    SEONGBUK("290", "성북구"),
    GANGBUK("305", "강북구"),
    DOBONG("320", "도봉구"),
    NOWON("350", "노원구"),
    EUNPYEONG("380", "은평구"),
    SEODAEMUN("410", "서대문구"),
    MAPO("440", "마포구"),
    YANGCHEON("470", "양천구"),
    GANGSEO("500", "강서구"),
    GURO("530", "구로구"),
    GEUMCHEON("545", "금천구"),
    YEONGDEUNGPO("560", "영등포구"),
    DONGJAK("590", "동작구"),
    GWANAK("620", "관악구"),
    SEOCHO("650", "서초구"),
    GANGNAM("680", "강남구"),
    SONGPA("710", "송파구"),
    GANGDONG("740", "강동구");

    /** 서울특별시 시도코드 (활용가이드 3.2) */
    public static final String SIDO_CODE_SEOUL = "11";

    private final String code;
    private final String koreanName;

    SeoulDistrict(String code, String koreanName) {
        this.code = code;
        this.koreanName = koreanName;
    }

    public String getCode() {
        return code;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public static List<SeoulDistrict> all() {
        return Arrays.asList(values());
    }
}
