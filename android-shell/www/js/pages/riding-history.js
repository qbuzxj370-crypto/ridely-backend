import { requireLoginOrRedirect } from '../auth.js';
import { listSessions } from '../ride-storage.js';

// 라이딩 기록은 서버에 없다 — 전부 이 기기의 localStorage에서만 읽는다
// (docs/shared/0918/LOCATION_PRIVACY_ARCHITECTURE.md). 앱 삭제·기기 변경 시 사라진다.
export function render(container) {
  if (!requireLoginOrRedirect('riding-history')) return;
  load(container);
}

function load(container) {
  const body = container.querySelector('#rh-body');
  const items = listSessions();
  if (!items.length) {
    body.innerHTML = '<div class="empty-state">아직 라이딩 기록이 없어요</div>';
    return;
  }
  body.innerHTML = '';
  items.forEach((s) => {
    const el = document.createElement('div');
    el.className = 'card';
    const dateLabel = new Date(s.endedAt).toLocaleString();
    el.innerHTML = `
      <strong>${s.distanceKm}km</strong>
      <div class="badge">평균 ${s.avgSpeedKmh}km/h</div>
      <div>${dateLabel}</div>
    `;
    body.appendChild(el);
  });
}
