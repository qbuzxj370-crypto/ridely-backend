import { createMap, addMarker } from '../map.js';
import { apiFetch } from '../api.js';

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
};

export function render(container) {
  let map = null;
  let infraMarkers = [];
  let searchCenterMarker = null;

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

  async function searchInfra(cont, mapInstance, center) {
    const summaryEl = cont.querySelector('#infra-summary-body');
    const radiusM = parseInt(cont.querySelector('#home-radius').value, 10) || 1000;
    const types = Array.from(cont.querySelectorAll('.home-type:checked')).map((el) => el.value);

    summaryEl.textContent = '불러오는 중...';
    summaryEl.classList.add('empty-state');

    try {
      const query = types.length ? `&types=${types.join(',')}` : '';
      const data = await apiFetch(`/pois/nearby?lat=${center.lat}&lng=${center.lng}&radiusM=${radiusM}${query}`);
      clearInfraMarkers();

      const counts = {};
      (data.items || []).forEach((item) => {
        const key = item.type === 'ROUTE_FACILITY' ? item.facilityType : item.type;
        counts[key] = (counts[key] || 0) + 1;
        const marker = addMarker(mapInstance, item.lat, item.lng, `${TYPE_LABELS[key] || key} · ${item.name || ''}`);
        infraMarkers.push(marker);
      });

      const label = Object.entries(counts)
        .map(([k, v]) => `${TYPE_LABELS[k] || k} ${v}`)
        .join(' · ');
      summaryEl.textContent = label || `반경 ${radiusM}m 안에 표시할 인프라가 없어요`;
      summaryEl.classList.remove('empty-state');
    } catch (e) {
      summaryEl.textContent = '인프라 정보를 불러오지 못했어요 (' + (e.message || '오류') + ')';
    }
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
