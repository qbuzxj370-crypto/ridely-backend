import { createMap, addMarker, addTourMarker } from '../map.js';
import { apiFetch } from '../api.js';
import { buildTourCard } from '../tour-card.js';

// 여의도한강공원 — 서비스 지역 안이라 확인하기 좋다 (FRONTEND_GUIDE.md 5장).
const DEFAULT_CENTER = { lat: 37.5265, lng: 126.9339 };

const TYPE_LABELS = {
  WATER: '급수대',
  TOILET: '화장실',
  CERT_CENTER: '인증센터',
  AIR_PUMP: '공기주입기',
  REPAIR_SHOP: '수리소',
  BIKE_STATION: '따릉이',
  ACCIDENT_ZONE: '사고다발지',
  TOUR: '관광지',
};

// TourAPI 관광타입: 12=관광지, 14=문화시설. 음식점(39)은 자전거 정보와 결이 달라 뺀다.
const TOUR_CONTENT_TYPES = '12,14';

/**
 * 반경 내 관광지. /tours/nearby는 결과가 0건이면 200+빈 배열이 아니라 404 POI-001을 낸다
 * (/pois/nearby와 다르다). 여기서는 0건이 정상 상태라 그대로 두면 관광지가 없는 곳을 볼
 * 때마다 에러 처리가 된다 — POI-001만 빈 결과로 바꾸고 나머지 오류는 그대로 던진다.
 */
async function fetchNearbyTours(center, radiusM) {
  try {
    return await apiFetch(
      `/tours/nearby?lat=${center.lat}&lng=${center.lng}&radiusM=${radiusM}&contentTypeIds=${TOUR_CONTENT_TYPES}`);
  } catch (e) {
    if (e.code === 'POI-001') return { items: [] };
    throw e;
  }
}

export function render(container) {
  let map = null;
  let infraMarkers = [];
  let searchCenterMarker = null;
  // 인프라·관광지 두 요청이 나가므로 검색을 연달아 누르면 늦게 온 옛 응답이 새 결과를 덮을
  // 수 있다. 가장 최근 검색의 응답만 화면에 반영한다.
  let searchSeq = 0;

  container.querySelector('#home-tour-close').addEventListener('click', hideTourDetail);

  initMap(container).then((m) => {
    map = m;
    if (map) searchInfra(container, map, DEFAULT_CENTER);
  });

  wireLocationSearch(container, () => map, (nextMap) => { map = nextMap; }, (marker) => {
    if (searchCenterMarker) searchCenterMarker.setMap(null);
    searchCenterMarker = marker;
  });

  container.querySelector('#home-infra-search').addEventListener('click', () => {
    if (!map) return;
    const center = map.getCenter();
    searchInfra(container, map, { lat: center.getLat(), lng: center.getLng() });
  });

  function clearInfraMarkers() {
    infraMarkers.forEach((m) => m.setMap(null));
    infraMarkers = [];
  }

  function showTourDetail(tour) {
    const box = container.querySelector('#home-tour-detail');
    const body = container.querySelector('#home-tour-body');
    if (!box || !body) return; // 마커를 누르기 전에 다른 탭으로 넘어간 경우
    // /tours/nearby의 항목은 상세 조회와 같은 TourAttractionDTO(개요까지 포함)라
    // GET /tours/{id}를 다시 부르지 않고 목록 데이터로 바로 카드를 그린다.
    body.replaceChildren(buildTourCard(tour));
    box.style.display = 'block';
  }

  function hideTourDetail() {
    const box = container.querySelector('#home-tour-detail');
    if (box) box.style.display = 'none';
  }

  async function searchInfra(cont, mapInstance, center) {
    const summaryEl = cont.querySelector('#infra-summary-body');
    // 카카오맵 첫 로딩(몇 초 걸릴 수 있음)이 끝나기 전에 사용자가 다른 탭으로 넘어가면,
    // 이 콜백이 뒤늦게 실행될 때 cont는 이미 다른 화면의 DOM이라 이 요소가 없다.
    if (!summaryEl) return;
    const radiusM = parseInt(cont.querySelector('#home-radius').value, 10) || 1000;
    const types = Array.from(cont.querySelectorAll('.home-type:checked')).map((el) => el.value);
    const includeTours = cont.querySelector('#home-tour').checked;

    // 예전엔 인프라 종류를 전부 끄면 서버가 「미지정=전체」로 받아 전 종류를 돌려줬다. 관광지
    // 체크박스가 생기면서 「관광지만」이 정상 선택이 됐으므로, 종류를 안 고른 상태는 요청 없이
    // 안내만 한다.
    if (!types.length && !includeTours) {
      summaryEl.textContent = '표시할 종류를 하나 이상 선택해주세요';
      summaryEl.classList.add('empty-state');
      return;
    }

    const seq = ++searchSeq;
    summaryEl.textContent = '불러오는 중...';
    summaryEl.classList.add('empty-state');

    // 두 요청은 서로 독립이다 — 한쪽이 실패해도 다른 쪽 결과는 그대로 보여준다.
    const infraRequest = types.length
      ? apiFetch(`/pois/nearby?lat=${center.lat}&lng=${center.lng}&radiusM=${radiusM}&types=${types.join(',')}`)
      : Promise.resolve(null);
    const tourRequest = includeTours ? fetchNearbyTours(center, radiusM) : Promise.resolve(null);
    const [infra, tours] = await Promise.allSettled([infraRequest, tourRequest]);
    if (seq !== searchSeq) return;

    const infraFailed = types.length > 0 && infra.status === 'rejected';
    const toursFailed = includeTours && tours.status === 'rejected';
    if ((!types.length || infraFailed) && (!includeTours || toursFailed)) {
      const reason = infraFailed ? infra.reason : tours.reason;
      summaryEl.textContent = '주변 정보를 불러오지 못했어요 (' + ((reason && reason.message) || '오류') + ')';
      return;
    }

    clearInfraMarkers();
    hideTourDetail(); // 이전 검색의 관광지 카드는 새 결과와 무관해진다

    const counts = {};
    const infraItems = infra.status === 'fulfilled' && infra.value ? infra.value.items || [] : [];
    infraItems.forEach((item) => {
      const key = item.type === 'ROUTE_FACILITY' ? item.facilityType : item.type;
      counts[key] = (counts[key] || 0) + 1;
      infraMarkers.push(addMarker(mapInstance, item.lat, item.lng, `${TYPE_LABELS[key] || key} · ${item.name || ''}`));
    });

    const tourItems = tours.status === 'fulfilled' && tours.value ? tours.value.items || [] : [];
    tourItems.forEach((tour) => {
      counts.TOUR = (counts.TOUR || 0) + 1;
      infraMarkers.push(addTourMarker(mapInstance, tour.lat, tour.lng, tour.title, () => showTourDetail(tour)));
    });

    const label = Object.entries(counts)
      .map(([k, v]) => `${TYPE_LABELS[k] || k} ${v}`)
      .join(' · ');
    const notes = [];
    if (infraFailed) notes.push('인프라는 불러오지 못했어요');
    if (toursFailed) notes.push('관광지는 불러오지 못했어요');
    const base = label || `반경 ${radiusM}m 안에 표시할 인프라·관광지가 없어요`;
    summaryEl.textContent = notes.length ? `${base} (${notes.join(', ')})` : base;
    summaryEl.classList.remove('empty-state');
  }

  async function initMap() {
    try {
      return await createMap('home-map', DEFAULT_CENTER);
    } catch (e) {
      console.error('kakao map load failed', e);
      const box = container.querySelector('#home-map');
      if (box) box.outerHTML = `<div class="error-banner">지도를 불러오지 못했어요: ${e.message}</div>`;
      return null;
    }
  }
}

/** 장소 검색으로 지도를 이동한다 — route-plan.js의 검색 패턴과 동일 */
function wireLocationSearch(container, getMap, setMap, setCenterMarker) {
  const input = container.querySelector('#home-search-query');
  const btn = container.querySelector('#home-search-btn');
  const resultsBox = container.querySelector('#home-search-results');

  btn.addEventListener('click', async () => {
    const query = input.value.trim();
    if (!query) return;
    resultsBox.innerHTML = '검색 중...';
    try {
      const data = await apiFetch(`/geo/search?query=${encodeURIComponent(query)}`);
      const items = data.items || data || [];
      if (!items.length) {
        resultsBox.innerHTML = '<div class="empty-state">검색 결과가 없어요</div>';
        return;
      }
      resultsBox.innerHTML = '';
      items.forEach((place) => {
        const row = document.createElement('div');
        row.className = 'list-item';
        row.style.cursor = 'pointer';
        row.innerHTML = `<span>${place.placeName}${place.inServiceArea ? '' : ' <span class="badge badge-danger">지역 밖</span>'}</span>`;
        row.addEventListener('click', () => {
          const map = getMap();
          if (!map) return;
          const point = new kakao.maps.LatLng(place.lat, place.lng);
          map.setCenter(point);
          const marker = new kakao.maps.Marker({ position: point, title: place.placeName });
          marker.setMap(map);
          setCenterMarker(marker);
          resultsBox.innerHTML = '';
          input.value = place.placeName;
        });
        resultsBox.appendChild(row);
      });
    } catch (e) {
      resultsBox.innerHTML = `<div class="error-banner">${e.message}</div>`;
    }
  });
}
