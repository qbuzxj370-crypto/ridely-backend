package kr.ridely.service;

import kr.ridely.dao.PoiSpatialDao;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 인프라 POI 전체 조회의 서버 메모리 캐시 테스트.
 *
 * 전체 조회는 인증 없이 열려 있고 요청 제한이 따로 없어서, 반복 요청이 그대로 DB 부담이 되지
 * 않게 몇 분간 결과를 들고 있는다(PoiServiceImpl.allCacheSeconds). 다른 통합 테스트는 데이터를
 * 바꾸고 바로 응답을 확인하므로 이 값을 0으로 두고(AbstractIntegrationTest), 여기서만 켠다.
 *
 * 캐시가 「켜졌을 때」 무엇을 보장하는지를 고정한다 - 결과 재사용, 만료 뒤 갱신, 그리고 만료
 * 순간 요청이 몰려도 DB 조회가 한 번만 나가는 것.
 */
class PoiAllCacheTest extends AbstractIntegrationTest {

    @Autowired
    private PoiService poiService;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoSpyBean
    private PoiSpatialDao poiSpatialDao;

    private Object target;

    @BeforeEach
    void 준비() {
        jdbcClient.sql("TRUNCATE TABLE route_facility RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE bike_station RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE repair_shop RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE accident_zone RESTART IDENTITY").update();
        시설("첫 급수대");

        target = AopUtils.isAopProxy(poiService) ? AopTestUtils.getTargetObject(poiService) : poiService;
        ReflectionTestUtils.setField(target, "allCacheSeconds", 300L);
        ReflectionTestUtils.setField(target, "allCache", null);
        clearInvocations(poiSpatialDao);
    }

    @AfterEach
    void 정리() {
        // 다른 테스트에 캐시가 새지 않게 원래대로(테스트는 0) 돌려놓는다
        ReflectionTestUtils.setField(target, "allCacheSeconds", 0L);
        ReflectionTestUtils.setField(target, "allCache", null);
    }

    @Test
    @DisplayName("캐시 안에서는 데이터가 바뀌어도 같은 결과를 재사용하고 DB를 다시 읽지 않는다")
    void 캐시_안에서는_재사용() {
        assertThat(poiService.findAll().getTotalCount()).isEqualTo(1);

        시설("캐시 뒤에 적재된 급수대");

        assertThat(poiService.findAll().getTotalCount()).as("캐시가 살아 있는 동안은 옛 결과").isEqualTo(1);
        verify(poiSpatialDao, times(1)).findAllRouteFacilities();
    }

    @Test
    @DisplayName("만료되면 다시 읽어 바뀐 데이터를 반영한다")
    void 만료되면_갱신() {
        assertThat(poiService.findAll().getTotalCount()).isEqualTo(1);
        시설("만료 뒤 보일 급수대");

        // 캐시를 만든 시각을 10분 전으로 돌려 만료시킨다(실제 시간을 기다리지 않는다)
        ReflectionTestUtils.setField(target, "allCacheLoadedNanos", System.nanoTime() - 600L * 1_000_000_000L);

        assertThat(poiService.findAll().getTotalCount()).isEqualTo(2);
        verify(poiSpatialDao, times(2)).findAllRouteFacilities();
    }

    @Test
    @DisplayName("캐시가 비어 있을 때 요청이 몰려도 DB 조회는 한 번만 나간다")
    void 동시_요청은_한_번만_조회() throws Exception {
        int 동시_요청 = 24;
        ExecutorService pool = Executors.newFixedThreadPool(동시_요청);
        try {
            List<Callable<Integer>> 작업 = new ArrayList<>();
            for (int i = 0; i < 동시_요청; i++) {
                작업.add(() -> poiService.findAll().getTotalCount());
            }
            for (Future<Integer> 결과 : pool.invokeAll(작업)) {
                assertThat(결과.get()).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
        verify(poiSpatialDao, times(1)).findAllRouteFacilities();
    }

    @Test
    @DisplayName("0이면 캐시를 쓰지 않고 매번 읽는다")
    void 꺼져_있으면_매번_조회() {
        ReflectionTestUtils.setField(target, "allCacheSeconds", 0L);

        poiService.findAll();
        시설("바로 보일 급수대");

        assertThat(poiService.findAll().getTotalCount()).isEqualTo(2);
        verify(poiSpatialDao, times(2)).findAllRouteFacilities();
    }

    private void 시설(String name) {
        jdbcClient.sql("""
                        INSERT INTO route_facility (facility_type, facility_name, geom)
                        VALUES ('WATER', :name, ST_SetSRID(ST_MakePoint(126.9339, 37.5265), 4326))
                        """)
                .param("name", name)
                .update();
    }
}
