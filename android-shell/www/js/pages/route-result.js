import { createMap, addMarker, drawPolyline, drawDangerZonePolygon, fitBounds } from '../map.js';
import { apiFetch } from '../api.js';
import { requireLoginOrRedirect } from '../auth.js';
import state from '../state.js';

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

  initMap(container, route, dangerZones);

  container.querySelector('#rr-save').addEventListener('click', () => saveRoute(route));
}

async function initMap(container, route, dangerZones) {
  try {
    const geo = JSON.parse(route.routeGeoJson);
    const coords = geo.coordinates || [];
    if (!coords.length) return;

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
  } catch (e) {
    console.error('route map render failed', e);
    const box = container.querySelector('#rr-map');
    if (box) box.outerHTML = `<div class="error-banner">경로 지도를 불러오지 못했어요: ${e.message}</div>`;
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
