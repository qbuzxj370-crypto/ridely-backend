package kr.ridely.infra.tourapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TourAPI 목록 응답 JSON 파서.
 *
 * 전용 ObjectMapper를 쓰는 이유:
 *   TourAPI 응답에는 결과 0건일 때 items가 객체가 아닌 빈 문자열("")로 오는 특성이 있다.
 *   이를 허용하는 설정(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)이 필요한데,
 *   전역 ObjectMapper에 걸면 우리 API 전체의 역직렬화 동작이 바뀌므로 여기서만 적용한다.
 */
@Component
public class TourApiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(TourApiResponseParser.class);

    /** 제공기관 정상 응답 코드 */
    private static final String RESULT_CODE_OK = "0000";

    /** 결과 없음. 오류가 아니라 정상 상황으로 취급한다 */
    private static final String RESULT_CODE_NO_DATA = "03";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            // 0건일 때 "items": "" 로 오는 것을 null로 처리
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            // 우리가 선언하지 않은 응답 필드(areacode, cat1 등)는 무시
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * 목록 응답 파싱.
     *
     * @param json TourApiClient가 받아온 응답 원문
     * @return 파싱 결과. 결과 0건이면 items()가 빈 리스트
     * @throws BusinessException 파싱 실패 또는 제공기관 오류 코드
     */
    public TourApiListResponse parseList(String json) {
        TourApiListResponse parsed;
        try {
            parsed = objectMapper.readValue(json, TourApiListResponse.class);
        } catch (Exception e) {
            log.error("TourAPI 응답 파싱 실패: {}", abbreviate(json), e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        String code = parsed.resultCode();
        if (code != null && !RESULT_CODE_OK.equals(code) && !RESULT_CODE_NO_DATA.equals(code)) {
            log.error("TourAPI 오류 응답: code={}, msg={}", code, parsed.resultMsg());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        return parsed;
    }

    /** 로그에 응답 전문이 쏟아지지 않도록 앞부분만 남긴다 */
    private String abbreviate(String json) {
        if (json == null) return "null";
        return json.length() <= 500 ? json : json.substring(0, 500) + "...(생략)";
    }
}
