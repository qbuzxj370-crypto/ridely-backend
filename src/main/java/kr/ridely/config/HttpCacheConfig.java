package kr.ridely.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * HTTP 캐시(ETag) 설정.
 *
 * <h3>왜 이 경로에만 거는가</h3>
 *
 * {@code GET /api/v1/pois/all}은 적재된 인프라 전부를 내려주는 큰 응답이라(약 5,500건) 앱이 매번
 * 통째로 받으면 낭비다. 응답 본문 해시를 ETag로 붙여 두면 앱이 {@code If-None-Match}로 다시 물었을
 * 때 바뀐 게 없으면 본문 없이 304만 돌려준다.
 *
 * ShallowEtagHeaderFilter는 컨트롤러가 응답을 다 만든 뒤 본문을 해시하는 방식이라 DB 조회 자체를
 * 줄여주지는 않는다 — 줄이는 것은 전송량이다. 그 정도로 충분하다고 본 이유는 적재 데이터가 가끔만
 * 바뀌어 앱이 Cache-Control(max-age)로 대부분 요청 자체를 안 보내기 때문이다.
 *
 * <b>다른 경로에는 걸지 않는다.</b> 이 필터는 응답 전체를 메모리에 버퍼링한다. 인증이 필요한
 * 응답이나 큰 응답에 무분별하게 걸면 부담이 된다.
 */
@Configuration
public class HttpCacheConfig {

    /**
     * ⚠️ <b>ETag는 반드시 약한(weak, {@code W/"..."}) 형태여야 한다.</b>
     *
     * Tomcat은 <b>강한 ETag가 붙은 응답을 압축하지 않는다.</b> 압축하면 본문 바이트가 달라져 강한
     * ETag의 의미(바이트 단위 동일)가 깨지기 때문이다. 기본값(강한 ETag)으로 두면 gzip이 조용히
     * 꺼져서, 실데이터 기준 약 525KB가 압축 없이 나간다. 2026-09-19 실측에서 같은 서버의
     * /pois/nearby는 gzip인데 /pois/all만 압축이 안 되는 것으로 발견했다.
     *
     * 약한 ETag로도 If-None-Match 조건부 요청(304)은 그대로 동작한다.
     * MockMvc 통합 테스트에는 Tomcat 압축이 없어서 이 문제를 테스트로는 못 잡는다 - 그래서 ETag
     * 형태 자체를 테스트로 고정해 둔다(PoiAllTest).
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> poiAllEtagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter();
        filter.setWriteWeakETag(true);
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/pois/all");
        registration.setName("poiAllEtagFilter");
        return registration;
    }
}
