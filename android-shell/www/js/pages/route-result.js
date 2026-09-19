import { createMap, addMarker, drawPolyline, drawDangerZonePolygon, fitBounds, panTo } from '../map.js';
import { apiFetch } from '../api.js';
import { requireLoginOrRedirect } from '../auth.js';
import state from '../state.js';

// 백엔드 WaypointDTO.type → 배지 라벨. ROUTE_FACILITY는 급수대·화장실·인증센터가 한 종류로
// 묶여 내려오고 세부 종류 필드가 없어서 「편의시설」로 통칭할 수밖에 없다.
const WAYPOINT_TYPE_LABELS = {
  TOUR_ATTRACTION: '관광지',
  BIKE_STATION: '따릉이',
  REPAIR_SHOP: '수리센터',
  ROUTE_FACILITY: '편의시설',
  BIKE_PARKING: '보관소',
};

export function render(container) {
  const route = state.lastRecommend;
  if (!route) {
    container.innerHTML = '<div class="screen"><div class="empty-state">추천받은 코스가 없어요. 홈에서 다시 시도해주세요.</div></div>';
    return;
  }

  container.querySelector('#rr-title').textContent = route.aiTitle || `${route.totalDistanceKm}km 라이딩 코스`;
  container.querySelector('#rr-comment').textContent = route.aiCoachComment || '';
  container.querySelector('#rr-distance').textContent = route.totalDistanceKm;
  container.querySelector('#rr-duration').textContent = route.estimatedDurationMin;
  container.querySelector('#rr-ascent').textContent = route.totalAscentM;
  container.querySelector('#rr-intensity').textContent = route.intensityLevel;
  container.querySelector('#rr-next-step').textContent = route.aiNextStepSuggestion || '-';

  const highlightsEl = container.querySelector('#rr-highlights');
  (route.aiHighlights || []).forEach((h) => {
    const li = document.createElement('li');
    li.textContent = h;
    highlightsEl.appendChild(li);
  });

  // FALLBACK이면 "AI 코치"로 보이면 안 된다 — 규칙 기반 표시로 대체 (FRONTEND_GUIDE.md 5장)
  if (route.aiProvider === 'FALLBACK') {
    container.querySelector('#rr-fallback-badge').style.display = 'inline-block';
  }

  const dangerZones = route.passingDangerZones || [];
  if (dangerZones.length > 0) {
    container.querySelector('#rr-danger-card').style.display = 'block';
    container.querySelector('#rr-danger-alert').textContent = route.aiDangerZoneAlert || '경로 중 사고다발지 구간을 지납니다.';
  }

  // 목록 클릭이 지도를 옮기려면 지도 객체가 필요한데, 카카오 SDK를 처음 불러오는 동안은
  // 아직 없다. 그 사이의 클릭은 지도 이동만 건너뛰고 목록은 그대로 동작한다.
  let map = null;
  renderWaypoints(container, route.waypoints || [], () => map);
  initMap(container, route, dangerZones).then((m) => { map = m || null; });

  container.querySelector('#rr-save').addEventListener('click', () => saveRoute(route));
}

/**
 * 경유지 목록. 지도에 이름만 달린 마커로 찍히고 끝나던 것을, 종류·이름·출발점 기준 거리와
 * AI가 쓴 「왜 이곳인지」 문장(reason)으로 보여준다 — 다른 지도 앱과 갈리는 지점이 reason이다.
 *
 * 이름·reason은 AI/외부 문자열이라 innerHTML에 넣지 않고 textContent로만 채운다.
 * reason은 FALLBACK 코스면 항상 비고, 아닐 때도 비어 있을 수 있어서 없으면 그 줄만 뺀다.
 */
function renderWaypoints(container, waypoints, getMap) {
  if (!waypoints.length) return;

  const listEl = container.querySelector('#rr-waypoints');
  // 코스 진행 순서(출발점 기준 거리)로 보여준다. 거리가 없는 항목은 맨 뒤로.
  const ordered = [...waypoints].sort((a, b) => {
    const da = a.distanceFromStartKm == null ? Infinity : Number(a.distanceFromStartKm);
    const db = b.distanceFromStartKm == null ? Infinity : Number(b.distanceFromStartKm);
    if (da === db) return 0;
    return da < db ? -1 : 1;
  });

  ordered.forEach((wp) => {
    const item = document.createElement('div');
    item.className = 'wp-item';

    const head = document.createElement('div');
    head.className = 'wp-head';

    const badge = document.createElement('span');
    badge.className = wp.type === 'TOUR_ATTRACTION' ? 'badge badge-tour' : 'badge';
    badge.textContent = WAYPOINT_TYPE_LABELS[wp.type] || '경유지';
    head.appendChild(badge);

    const name = document.createElement('span');
    name.className = 'wp-name';
    name.textContent = wp.name || '';
    head.appendChild(name);

    if (wp.distanceFromStartKm != null) {
      const dist = document.createElement('span');
      dist.className = 'wp-dist';
      dist.textContent = `${Number(wp.distanceFromStartKm).toFixed(1)}km 지점`;
      head.appendChild(dist);
    }
    item.appendChild(head);

    if (wp.reason) {
      const reason = document.createElement('p');
      reason.className = 'wp-reason';
      reason.textContent = wp.reason;
      item.appendChild(reason);
    }

    item.addEventListener('click', () => {
      const map = getMap();
      if (map) panTo(map, wp.lat, wp.lng);
    });
    listEl.appendChild(item);
  });

  container.querySelector('#rr-waypoints-card').style.display = 'block';
}

async function initMap(container, route, dangerZones) {
  try {
    const geo = JSON.parse(route.routeGeoJson);
    const coords = geo.coordinates || [];
    if (!coords.length) return null;

    const [firstLng, firstLat] = coords[0];
    const map = await createMap('rr-map', { lat: firstLat, lng: firstLng, level: 6 });
    drawPolyline(map, coords);

    (route.waypoints || []).forEach((wp) => addMarker(map, wp.lat, wp.lng, wp.name));
    dangerZones.forEach((zone) => {
      if (!zone.polygonGeoJson) return;
      try {
        const polyGeo = JSON.parse(zone.polygonGeoJson);
        const ring = (polyGeo.coordinates && polyGeo.coordinates[0]) || [];
        if (ring.length) drawDangerZonePolygon(map, ring);
      } catch (e) { /* 폴리곤 파싱 실패는 지도 표시만 건너뛴다 */ }
    });

    fitBounds(map, coords.map(([lng, lat]) => [lat, lng]));
    return map;
  } catch (e) {
    console.error('route map render failed', e);
    const box = container.querySelector('#rr-map');
    if (box) box.outerHTML = `<div class="error-banner">경로 지도를 불러오지 못했어요: ${e.message}</div>`;
    return null;
  }
}

async function saveRoute(route) {
  if (!requireLoginOrRedirect('route-result')) return;
  try {
    await apiFetch('/saved-routes', {
      method: 'POST',
      auth: true,
      body: { recommendedRouteId: route.recommendedRouteId },
    });
    alert('저장했어요!');
  } catch (e) {
    alert('저장하지 못했어요: ' + e.message);
  }
}
