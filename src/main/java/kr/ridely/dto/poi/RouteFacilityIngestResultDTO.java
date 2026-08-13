package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 자전거길 주변시설 적재 결과.
 * POST /api/v1/poc/seed/route-facilities 응답.
 *
 * 다음이 어긋나면 파싱이나 원본을 의심해야 한다.
 *   - 판별 인코딩이 예상과 다르다 (원본 배포본은 CP949)
 *   - 미상 구분값이 0이 아니다 → 인코딩 오판 신호
 *   - 종류별 건수가 알려진 값과 다르다 (화장실 793·급수대 185·인증센터 92·공기주입기 63)
 *   - 노선 연결률이 낮다 → 노선 형상 미적재이거나 매칭 반경이 좁다
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteFacilityIngestResultDTO {

    /** CSV를 읽어낸 인코딩. 원본 배포본은 CP949(MS949)다 */
    private String charsetName;

    /** CSV 데이터 행 수 (헤더·빈 줄 제외). 원본은 1,133행 */
    private int dataRowCount;

    /** 형식이 어긋나 건너뛴 행 수. 원본에는 결손이 없어 0이 정상이다 */
    private int skippedRowCount;

    /** 완전 중복이라 제거한 행 수. 원본에 109건 있다 */
    private int duplicateRowCount;

    /**
     * 알려진 4종에 없는 구분값이 나온 행 수.
     * 0이 아니면 인코딩 판별이 틀렸을 가능성이 높다.
     */
    private int unknownLabelCount;

    /** 실제로 INSERT된 건수. 재실행하면 0이 정상이다(이미 다 있으므로) */
    private int insertedCount;

    /** 테이블 전체의 종류별 건수 */
    private Map<String, Integer> countByType;

    /** 노선에 연결된 시설 수 */
    private int linkedCount;

    /** 노선 연결에 사용한 반경 (m) */
    private int linkRadiusM;
}
