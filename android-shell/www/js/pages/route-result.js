import { createMap, addMarker, drawPolyline, drawDangerZonePolygon, fitBounds, panTo } from '../map.js';
import { apiFetch } from '../api.js';
import { requireLoginOrRedirect } from '../auth.js';
import state from '../state.js';
import { escapeHtml } from '../dom.js';
import { fetchTourDetail, buildTourCard } from '../tour-card.js';
import { loadAllInfra } from '../infra-store.js';
import { navigate } from '../router.js';

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
  let openSheet = null;
  // 코스 진행 순서(출발점 기준 거리)로 보여준다. 거리가 없는 항목은 맨 뒤로.
  const ordered = [...waypoints].sort((a, b) => {
    const da = a.distanceFromStartKm == null ? Infinity : Number(a.distanceFromStartKm);
    const db = b.distanceFromStartKm == null ? Infinity : Number(b.distanceFromStartKm);
    if (da === db) return 0;
    return da < db ? -1 : 1;
  });

  ordered.forEach((wp, index) => {
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
      if (openSheet) openSheet.remove();
      openSheet = openWaypointSheet(container, wp);
    });
    listEl.appendChild(item);
    item.style.animationDelay = `${Math.min(index, 10) * 40}ms`;
  });

  container.querySelector('#rr-waypoints-card').style.display = 'block';
}

function showSheetStatus(body, message) {
  body.replaceChildren();
  const el = document.createElement('div');
  el.className = 'wp-detail-status';
  el.textContent = message;
  body.appendChild(el);
}

function showSheetLoading(body, message) {
  body.replaceChildren();
  const el = document.createElement('div');
  el.className = 'wp-detail-status';
  const spinner = document.createElement('span');
  spinner.className = 'spinner-inline';
  spinner.setAttribute('aria-hidden', 'true');
  el.append(spinner, document.createTextNode(message));
  body.appendChild(el);
}

function addSheetLine(body, label, value) {
  if (value == null || value === '') return;
  const line = document.createElement('div');
  line.className = 'wp-sheet-line';
  const title = document.createElement('strong');
  title.textContent = label;
  const detail = document.createElement('span');
  detail.textContent = String(value);
  line.append(title, detail);
  body.appendChild(line);
}

function renderInfraDetail(body, wp, item) {
  body.replaceChildren();
  if (!item) {
    showSheetStatus(body, '추가 정보가 없어요');
    return;
  }

  if (wp.type === 'ROUTE_FACILITY') {
    const facilityLabels = {
      WATER: '급수대', TOILET: '화장실', CERT_CENTER: '인증센터',
      AIR_PUMP: '공기주입기', ETC: '기타',
    };
    addSheetLine(body, '시설 종류', facilityLabels[item.facilityType] || item.facilityType);
  } else if (wp.type === 'REPAIR_SHOP') {
    addSheetLine(body, '주소', item.addr);
    addSheetLine(body, '전화', item.tel);
    addSheetLine(body, '운영 시간', item.operatingHours);
    addSheetLine(body, '이용 요금', item.isFree == null ? null : item.isFree ? '무료' : '유료');
  } else if (wp.type === 'BIKE_STATION') {
    showSheetStatus(body, '따릉이 대여소예요');
  }

  if (!body.hasChildNodes()) showSheetStatus(body, '추가 정보가 없어요');
}

const SHEET_DISMISS_DISTANCE = 90;

function wireSheetDrag(header, sheet, dismiss) {
  let startY = 0;
  let dy = 0;
  let dragging = false;

  header.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 1) return;
    startY = e.touches[0].clientY;
    dragging = true;
    sheet.style.transition = 'none';
  }, { passive: true });

  header.addEventListener('touchmove', (e) => {
    if (!dragging) return;
    dy = Math.max(0, e.touches[0].clientY - startY);
    sheet.style.transform = `translateY(${dy}px)`;
  }, { passive: true });

  header.addEventListener('touchend', () => {
    if (!dragging) return;
    dragging = false;
    sheet.style.transition = '';
    sheet.style.transform = '';
    if (dy > SHEET_DISMISS_DISTANCE) dismiss();
    dy = 0;
  }, { passive: true });
}

function openWaypointSheet(container, wp) {
  const root = container.querySelector('.screen');
  const overlay = document.createElement('div');
  overlay.className = 'wp-sheet-overlay';
  const backdrop = document.createElement('div');
  backdrop.className = 'wp-sheet-backdrop';
  const sheet = document.createElement('section');
  sheet.className = 'wp-sheet';
  sheet.setAttribute('role', 'dialog');
  sheet.setAttribute('aria-label', '경유지 상세 정보');
  const header = document.createElement('div');
  header.className = 'wp-sheet-header';
  const heading = document.createElement('div');
  const badge = document.createElement('span');
  badge.className = wp.type === 'TOUR_ATTRACTION' ? 'badge badge-tour' : 'badge';
  badge.textContent = WAYPOINT_TYPE_LABELS[wp.type] || '경유지';
  const name = document.createElement('h3');
  name.textContent = wp.name || '이름 없는 경유지';
  heading.append(badge, name);
  if (wp.distanceFromStartKm != null) {
    const distance = document.createElement('div');
    distance.className = 'wp-sheet-distance';
    distance.textContent = `${Number(wp.distanceFromStartKm).toFixed(1)}km 지점`;
    heading.appendChild(distance);
  }
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'wp-sheet-close';
  close.setAttribute('aria-label', '닫기');
  close.textContent = '×';
  const dismiss = () => {
    overlay.classList.remove('open');
    setTimeout(() => overlay.remove(), 250); // Match .wp-sheet transition duration.
  };
  wireSheetDrag(header, sheet, dismiss);
  close.addEventListener('click', dismiss);
  backdrop.addEventListener('click', dismiss);
  header.append(heading, close);
  const body = document.createElement('div');
  body.className = 'wp-sheet-body';
  if (wp.reason) {
    const reason = document.createElement('p');
    reason.className = 'wp-reason';
    reason.textContent = wp.reason;
    sheet.append(header, reason, body);
  } else {
    sheet.append(header, body);
  }
  overlay.append(backdrop, sheet);
  root.appendChild(overlay);
  requestAnimationFrame(() => overlay.classList.add('open'));

  if (wp.id == null) {
    showSheetStatus(body, '추가 정보가 없어요');
  } else if (wp.type === 'TOUR_ATTRACTION') {
    showSheetLoading(body, '불러오는 중...');
    fetchTourDetail(wp.id).then((tour) => {
      if (overlay.isConnected) body.replaceChildren(buildTourCard(tour));
    }).catch((e) => {
      if (!overlay.isConnected) return;
      showSheetStatus(body, e.code === 'COMMON-004'
        ? '관광지 정보를 찾을 수 없어요'
        : `${e.message || '불러오지 못했어요'} — 다시 눌러 재시도해요`);
    });
  } else {
    showSheetLoading(body, '불러오는 중...');
    loadAllInfra().then(({ items }) => {
      if (overlay.isConnected) renderInfraDetail(body, wp,
        items.find((item) => item.type === wp.type && item.id === wp.id));
    }).catch(() => {
      if (overlay.isConnected) showSheetStatus(body, '추가 정보를 불러오지 못했어요');
    });
  }
  return overlay;
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
    if (box) box.outerHTML = `<div class="error-banner">경로 지도를 불러오지 못했어요: ${escapeHtml(e.message)}</div>`;
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
    navigate('saved-routes');
  } catch (e) {
    alert('저장하지 못했어요: ' + e.message);
  }
}
