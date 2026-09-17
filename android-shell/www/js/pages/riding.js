import { apiFetch } from '../api.js';
import { requireLoginOrRedirect } from '../auth.js';
import { navigate } from '../router.js';
import state from '../state.js';
import { createMap, drawPolyline, drawDangerZonePolygon, fitBounds } from '../map.js';

// 여의도한강공원 — 추천 코스도 없고 GPS 첫 위치도 아직 없을 때 지도 초기 중심
const FALLBACK_CENTER = { lat: 37.5265, lng: 126.9339 };

const SAMPLE_INTERVAL_MS = 10000; // 가이드 기준: 10초 또는 50m 중 먼저 오는 것 — trackGeoJson 업로드용 샘플링
const SAMPLE_MIN_DISTANCE_M = 50;
const DANGER_ALERT_DISTANCE_M = 200; // ridely.route.danger-zone-alert-distance-m 기본값과 동일

// GPS 정확도가 이보다 나쁜(숫자가 큰) 갱신은 이동거리 계산에서 뺀다.
// 실측(2026-09-17 지쿠터 테스트)에서 정확도 필터·중복 누적 버그 때문에 평균속도가 터무니없이 튀었다.
const MAX_ACCEPTABLE_ACCURACY_M = 30;

// 한 번의 GPS 갱신 사이에 이 속도를 넘는 이동은 실제 주행이 아니라 GPS 튐으로 본다.
// 지쿠터 법정 최고속도가 25km/h라 여유를 크게 둬도 45면 충분하다.
const MAX_PLAUSIBLE_KMH = 45;

// 진행 중인 라이딩을 로컬에 계속 저장해둔다. 화면이 꺼지거나 앱이 백그라운드로 밀려나면
// 안드로이드가 프로세스를 죽일 수 있는데, 그러면 메모리에만 있던 기록이 통째로 날아간다
// (실측 2026-09-17: 지쿠터 테스트 중 실제로 겪었다). 이 키에 매 GPS 갱신마다 저장해두면
// 앱이 다시 켜졌을 때 render()가 이어서 추적을 재개할 수 있다.
const STORAGE_KEY = 'ridely.riding.active_session';

function haversineM(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

export function render(container) {
  if (!requireLoginOrRedirect('riding')) return;

  let watchId = null;
  let timerId = null;
  let startedAt = null;
  let lastSample = null; // trackGeoJson 샘플링 기준점 (10초/50m)
  let lastFix = null;    // 이동거리 누적 기준점 — 매 GPS 갱신마다 갱신, 샘플링과 무관
  let track = []; // [lng, lat]
  let distanceM = 0;
  let instantSpeedKmh = 0;
  let sessionId = null;
  let alertedZones = new Set();
  let dangerZones = (state.lastRecommend && state.lastRecommend.passingDangerZones) || [];

  let map = null;
  let liveMarker = null;
  let traveledPolyline = null;
  let traveledPath = []; // kakao.maps.LatLng[] — 지금까지 실제로 달린 경로(빵부스러기)

  const idleBox = container.querySelector('#riding-idle');
  const activeBox = container.querySelector('#riding-active');
  const distanceEl = container.querySelector('#riding-distance');
  const elapsedEl = container.querySelector('#riding-elapsed');
  const speedEl = container.querySelector('#riding-speed');
  const instantSpeedEl = container.querySelector('#riding-instant-speed');
  const alertEl = container.querySelector('#riding-alert');
  const selectedCard = container.querySelector('#riding-selected-card');
  const selectedSummary = container.querySelector('#riding-selected-summary');
  const savedListEl = container.querySelector('#riding-saved-list');
  const endBtn = container.querySelector('#riding-end');

  container.querySelector('#riding-start').addEventListener('click', startRiding);
  container.querySelector('#riding-start-free').addEventListener('click', () => {
    state.lastRecommend = null;
    updateSelectedSummary();
    startRiding();
  });
  endBtn.addEventListener('click', endRiding);

  updateSelectedSummary();
  loadSavedRoutes();
  resumeInterruptedSession();

  /** 죽전처럼 서비스 지역 밖에 있어 직접 주행 못 해볼 때도, 저장한 코스를 골라 지도·경로만 먼저 확인할 수 있게 한다 */
  async function loadSavedRoutes() {
    try {
      const data = await apiFetch('/saved-routes?page=0&size=20&sort=latest', { auth: true });
      const items = data.content || [];
      if (!items.length) {
        savedListEl.innerHTML = '<div class="empty-state">저장한 코스가 없어요</div>';
        return;
      }
      savedListEl.innerHTML = '';
      items.forEach((r) => {
        const row = document.createElement('div');
        row.className = 'list-item';
        row.style.cursor = 'pointer';
        row.innerHTML = `<span>${r.customName || r.aiTitle}</span><span class="badge">${r.totalDistanceKm}km</span>`;
        row.addEventListener('click', () => selectSavedRoute(r.recommendedRouteId));
        savedListEl.appendChild(row);
      });
    } catch (e) {
      savedListEl.innerHTML = `<div class="error-banner">${e.message}</div>`;
    }
  }

  async function selectSavedRoute(recommendedRouteId) {
    try {
      const route = await apiFetch(`/routes/${recommendedRouteId}`);
      state.lastRecommend = route;
      dangerZones = route.passingDangerZones || [];
      updateSelectedSummary();
    } catch (e) {
      alertEl.innerHTML = `<div class="error-banner">코스를 불러오지 못했어요: ${e.message}</div>`;
    }
  }

  function updateSelectedSummary() {
    const route = state.lastRecommend;
    if (!route) {
      selectedCard.style.display = 'none';
      return;
    }
    selectedCard.style.display = 'block';
    selectedSummary.textContent = `${route.aiTitle || route.totalDistanceKm + 'km 코스'} · ${route.totalDistanceKm}km`;
  }

  async function startRiding() {
    try {
      const body = state.lastRecommend ? { recommendedRouteId: state.lastRecommend.recommendedRouteId } : {};
      const session = await apiFetch('/riding-sessions', { method: 'POST', auth: true, body });
      sessionId = session.ridingSessionId;
    } catch (e) {
      alertEl.innerHTML = `<div class="error-banner">라이딩을 시작하지 못했어요: ${e.message}</div>`;
      return;
    }

    startedAt = Date.now();
    track = [];
    distanceM = 0;
    instantSpeedKmh = 0;
    lastSample = null;
    lastFix = null;
    alertedZones = new Set();
    traveledPath = [];

    idleBox.style.display = 'none';
    activeBox.style.display = 'block';
    alertEl.innerHTML = '';

    await initMap();
    beginTracking();
    saveProgress();
  }

  /**
   * 화면이 꺼졌다 켜지는 사이 안드로이드가 앱 프로세스를 죽였다가 다시 실행한 경우를 감지해
   * 진행 중이던 라이딩을 이어서 추적한다. 배터리 최적화·백그라운드 제한 때문에 실제로 겪었다.
   */
  async function resumeInterruptedSession() {
    let saved;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      saved = JSON.parse(raw);
    } catch (e) {
      return;
    }

    sessionId = saved.sessionId;
    startedAt = saved.startedAt;
    distanceM = saved.distanceM || 0;
    track = saved.track || [];
    alertedZones = new Set(saved.alertedZones || []);
    lastSample = track.length
      ? { lat: track[track.length - 1][1], lng: track[track.length - 1][0], time: Date.now() }
      : null;
    lastFix = lastSample ? { lat: lastSample.lat, lng: lastSample.lng, time: Date.now() } : null;

    if (saved.recommendedRouteId && (!state.lastRecommend || state.lastRecommend.recommendedRouteId !== saved.recommendedRouteId)) {
      try {
        state.lastRecommend = await apiFetch(`/routes/${saved.recommendedRouteId}`);
        dangerZones = state.lastRecommend.passingDangerZones || [];
      } catch (e) { /* 지도에 계획 경로만 다시 못 그릴 뿐, 추적 재개는 계속 진행한다 */ }
    }

    idleBox.style.display = 'none';
    activeBox.style.display = 'block';
    alertEl.innerHTML = '<div class="error-banner">이전 라이딩이 중단됐던 걸 이어서 추적합니다.</div>';
    distanceEl.textContent = (distanceM / 1000).toFixed(2);

    await initMap();
    traveledPath = track.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
    if (traveledPolyline) traveledPolyline.setPath(traveledPath);

    beginTracking();
  }

  function beginTracking() {
    timerId = setInterval(updateElapsed, 1000);
    watchId = navigator.geolocation.watchPosition(onPosition, onPositionError, {
      enableHighAccuracy: true,
      maximumAge: 2000,
      timeout: 15000,
    });
  }

  function saveProgress() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        sessionId,
        startedAt,
        distanceM,
        track,
        alertedZones: Array.from(alertedZones),
        recommendedRouteId: state.lastRecommend ? state.lastRecommend.recommendedRouteId : null,
      }));
    } catch (e) { /* localStorage 꽉 찼거나 비활성화면 무시 — 추적 자체는 막지 않는다 */ }
  }

  function clearProgress() {
    try { localStorage.removeItem(STORAGE_KEY); } catch (e) { /* 무시 */ }
  }

  /**
   * 지도를 띄운다 — 내비게이션처럼 계획한 경로·사고다발지·내 실시간 위치를 한 화면에 보여준다.
   * 추천 코스가 있으면 그 경로선을 그리고, 없으면(자유 주행) 빈 지도에서 시작해 GPS로 채운다.
   */
  async function initMap() {
    const route = state.lastRecommend;
    let center = FALLBACK_CENTER;
    let routeCoords = null;

    if (track.length) {
      // 이어서 추적하는 경우 지금까지 온 위치를 중심으로 잡는다
      center = { lat: track[track.length - 1][1], lng: track[track.length - 1][0] };
    }

    if (route && route.routeGeoJson) {
      try {
        const geo = JSON.parse(route.routeGeoJson);
        routeCoords = geo.coordinates;
        if (!track.length) {
          const [firstLng, firstLat] = routeCoords[0];
          center = { lat: firstLat, lng: firstLng };
        }
      } catch (e) {
        console.error('riding route geoJson parse failed', e);
      }
    }

    try {
      map = await createMap('riding-map', { ...center, level: 5 });
    } catch (e) {
      console.error('riding map load failed', e);
      map = null;
      return;
    }

    if (routeCoords) {
      drawPolyline(map, routeCoords); // 계획한 경로 (초록)
      fitBounds(map, routeCoords.map(([lng, lat]) => [lat, lng]));
    }

    dangerZones.forEach((zone) => {
      if (!zone.polygonGeoJson) return;
      try {
        const polyGeo = JSON.parse(zone.polygonGeoJson);
        const ring = (polyGeo.coordinates && polyGeo.coordinates[0]) || [];
        if (ring.length) drawDangerZonePolygon(map, ring);
      } catch (e) { /* 폴리곤 파싱 실패는 표시만 건너뛴다 */ }
    });

    // 지금까지 달린 경로 — 계획 경로와 구분되게 강조색(오렌지)으로, GPS 업데이트마다 setPath로 늘린다
    traveledPolyline = new kakao.maps.Polyline({
      path: traveledPath,
      strokeWeight: 6,
      strokeColor: '#FF7A45',
      strokeOpacity: 0.9,
      strokeStyle: 'solid',
    });
    traveledPolyline.setMap(map);
  }

  function onPosition(pos) {
    const { latitude, longitude, accuracy } = pos.coords;
    const now = Date.now();

    // 정확도가 나쁜 갱신(도심 협곡, 실내 근처 등)은 이동거리 계산에서 뺀다 — 그대로 두면
    // 오차 자체가 이동거리로 잡힌다. 위치 표시·위험구역 판정에는 그대로 쓴다.
    const accuracyOk = accuracy == null || accuracy <= MAX_ACCEPTABLE_ACCURACY_M;

    if (accuracyOk && lastFix) {
      const deltaM = haversineM(lastFix.lat, lastFix.lng, latitude, longitude);
      const deltaSec = (now - lastFix.time) / 1000;
      const impliedKmh = deltaSec > 0 ? (deltaM / deltaSec) * 3.6 : 0;

      // 순간 속도가 지쿠터로 물리적으로 불가능하면 GPS가 튄 것으로 보고 이번 이동은 버린다.
      // ⚠️ 예전 버전은 "10초/50m 샘플링용 마지막 지점"을 기준으로 매 GPS 갱신마다 거리를
      // 다시 더해서, 같은 구간이 여러 번 중복으로 누적됐다(실측: 지쿠터 테스트에서 평균속도가
      // 터무니없이 높게 나온 원인). 기준점을 매 갱신마다 갱신되는 lastFix로 분리해서 고쳤다.
      if (impliedKmh <= MAX_PLAUSIBLE_KMH) {
        distanceM += deltaM;
        instantSpeedKmh = impliedKmh;
      }
    }

    if (accuracyOk) {
      lastFix = { lat: latitude, lng: longitude, time: now };
    }

    const shouldSample =
      !lastSample ||
      now - lastSample.time >= SAMPLE_INTERVAL_MS ||
      haversineM(lastSample.lat, lastSample.lng, latitude, longitude) >= SAMPLE_MIN_DISTANCE_M;
    if (shouldSample) {
      track.push([longitude, latitude]);
      lastSample = { lat: latitude, lng: longitude, time: now };
    }

    checkDangerZones(latitude, longitude);
    updateLivePosition(latitude, longitude);
    distanceEl.textContent = (distanceM / 1000).toFixed(2);
    instantSpeedEl.textContent = instantSpeedKmh.toFixed(1);
    saveProgress();
  }

  /**
   * 네비게이션처럼 지도 위 내 위치를 옮기고 지도를 따라오게 한다.
   * 지금까지 달린 경로선도 매번 늘려 그린다 — GPS 업데이트마다 부르므로 잦다.
   */
  function updateLivePosition(lat, lng) {
    if (!map) return; // 지도 로드 실패했으면(카카오 SDK 문제 등) 위치 추적만 계속한다
    const point = new kakao.maps.LatLng(lat, lng);

    if (!liveMarker) {
      liveMarker = new kakao.maps.Marker({ position: point, title: '내 위치' });
      liveMarker.setMap(map);
    } else {
      liveMarker.setPosition(point);
    }

    traveledPath.push(point);
    if (traveledPolyline) traveledPolyline.setPath(traveledPath);

    map.setCenter(point); // 따라가기 — 사용자가 지도를 직접 움직이면 다음 업데이트에 다시 튕겨간다(단순화)
  }

  function checkDangerZones(lat, lng) {
    dangerZones.forEach((zone, idx) => {
      if (alertedZones.has(idx)) return;
      if (zone.lat == null || zone.lng == null) return;
      const d = haversineM(lat, lng, zone.lat, zone.lng);
      if (d <= DANGER_ALERT_DISTANCE_M) {
        alertedZones.add(idx);
        alertEl.innerHTML = `<div class="error-banner">⚠️ 사고다발지 근접 (${Math.round(d)}m) — ${zone.name || ''}</div>`;
      }
    });
  }

  function onPositionError(err) {
    alertEl.innerHTML = `<div class="error-banner">위치 추적 오류: ${err.message}</div>`;
  }

  function updateElapsed() {
    const sec = Math.floor((Date.now() - startedAt) / 1000);
    const mm = String(Math.floor(sec / 60)).padStart(2, '0');
    const ss = String(sec % 60).padStart(2, '0');
    elapsedEl.textContent = `${mm}:${ss}`;
    const hours = sec / 3600;
    const km = distanceM / 1000;
    // "평균 속도"는 정지 시간까지 포함한 총거리/총시간이라 멈춰 서있으면 계속 떨어진다 —
    // 정상 계산이지만 라이더가 보고 싶은 건 대개 지금 속도라, onPosition에서 구하는
    // "현재 속도"(riding-instant-speed)를 따로 보여준다. 최종 저장값은 평균 쪽을 그대로 쓴다.
    speedEl.textContent = hours > 0 ? (km / hours).toFixed(1) : '0.0';
  }

  async function endRiding() {
    // ⚠️ 성공을 확인하기 전엔 stopTracking()을 부르지 않는다. 예전엔 여기서 바로 멈췄는데,
    // 그러면 종료 API가 실패해서(연결 끊김 등) 사용자가 재시도하는 동안 실제로는 계속 타고
    // 있어도 그 구간 GPS가 하나도 안 잡혔다. 성공할 때까지는 추적을 계속 살려두고, 재시도
    // 시점의 최신 distanceM/track으로 다시 계산해서 보낸다 — 그래서 실패해도 놓치는 구간이 없다.
    const elapsedHours = (Date.now() - startedAt) / 3600000;
    const distanceKm = distanceM / 1000;
    const avgSpeedKmh = elapsedHours > 0 ? distanceKm / elapsedHours : 0;

    const body = {
      distanceKm: Math.round(distanceKm * 100) / 100,
      avgSpeedKmh: Math.round(avgSpeedKmh * 10) / 10,
      isCompleted: true,
      visitedPoiCount: 0,
      alertReceivedCount: alertedZones.size,
    };
    if (track.length >= 2) {
      body.trackGeoJson = JSON.stringify({ type: 'LineString', coordinates: track });
    }

    try {
      await apiFetch(`/riding-sessions/${sessionId}`, { method: 'PATCH', auth: true, body });
      stopTracking();
      clearProgress();
      alert(`라이딩 종료! ${body.distanceKm}km 달렸어요.`);
      navigate('riding-history');
    } catch (e) {
      // 화면을 idle로 되돌리지 않는다 — 되돌리면 "시작" 버튼이 새 세션을 만들어서 이 기록이
      // 사라진다. GPS 추적은 위에서 그대로 두었으니(stopTracking 미호출) 재시도하는 동안의
      // 이동도 계속 기록된다. 로컬 저장(saveProgress)은 이미 돼 있어 앱을 완전히 다시 켜도
      // 이어서 종료를 시도할 수 있다.
      alertEl.innerHTML = `<div class="error-banner">종료 처리에 실패했어요: ${e.message} — 연결 확인 후 종료 버튼을 다시 눌러주세요</div>`;
    }
  }

  function stopTracking() {
    if (watchId != null) navigator.geolocation.clearWatch(watchId);
    if (timerId != null) clearInterval(timerId);
    watchId = null;
    timerId = null;
  }

  // 라우터가 다른 화면으로 이동할 때 호출하는 정리 함수
  return function cleanup() {
    stopTracking();
  };
}
