package kr.ridely.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 페이지 응답 공통 껍데기.
 *
 * 목록 API가 둘(저장 경로·라이딩 세션)이라 각자 페이지 필드를 들고 있게 두지 않고 하나로 묶는다. 화면도 목록마다 다른 형태를 받으면 페이지네이션 컴포넌트를 재사용할 수 없다.
 *
 * Spring Data의 Page를 쓰지 않는 이유는 이 프로젝트가 MyBatis라 spring-data-commons가 의존성에 없어서다. 그것 하나 때문에 의존성을 늘리는 것보다 필드 다섯 개를 직접 두는 편이 가볍다.
 *
 * ApiResponse 안에 들어간다:
 *   { "success": true, "data": { "content": [...], "page": 0, ... }, "error": null }
 *
 * @param <T> 목록 항목 타입
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /** 현재 페이지의 항목들 */
    private List<T> content;

    /** 현재 페이지 번호. 0부터 시작한다 */
    private int page;

    /** 페이지당 항목 수 */
    private int size;

    /** 조건에 맞는 전체 항목 수 */
    private long totalElements;

    /** 전체 페이지 수 */
    private int totalPages;

    /**
     * 조회 결과와 전체 건수로 페이지 응답을 만든다.
     *
     * totalPages를 호출부마다 계산하면 올림 처리를 빠뜨리기 쉬워 여기서 한 번만 계산한다. 전체가 0건이면 0페이지다.
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
