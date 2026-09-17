import { apiFetch } from '../api.js';
import { requireLoginOrRedirect } from '../auth.js';

export function render(container) {
  if (!requireLoginOrRedirect('riding-history')) return;
  load(container);
}

async function load(container) {
  const body = container.querySelector('#rh-body');
  try {
    const data = await apiFetch('/riding-sessions?page=0&size=20', { auth: true });
    const items = data.content || [];
    if (!items.length) {
      body.innerHTML = '<div class="empty-state">아직 라이딩 기록이 없어요</div>';
      return;
    }
    body.innerHTML = '';
    items.forEach((s) => {
      const el = document.createElement('div');
      el.className = 'card';
      const status = s.endedAt ? '완료' : '진행 중';
      el.innerHTML = `
        <strong>${s.distanceKm != null ? s.distanceKm + 'km' : status}</strong>
        <div class="badge">${status}${s.avgSpeedKmh != null ? ' · 평균 ' + s.avgSpeedKmh + 'km/h' : ''}</div>
      `;
      body.appendChild(el);
    });
  } catch (e) {
    body.innerHTML = `<div class="error-banner">${e.message}</div>`;
  }
}
