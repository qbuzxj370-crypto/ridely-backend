import { apiFetch } from '../api.js';
import { escapeHtml } from '../dom.js';
import { requireLoginOrRedirect } from '../auth.js';
import { navigate } from '../router.js';
import state from '../state.js';

export function render(container) {
  if (!requireLoginOrRedirect('saved-routes')) return;
  load(container);
}

async function load(container) {
  const body = container.querySelector('#sr-body');
  try {
    const data = await apiFetch('/saved-routes?page=0&size=20&sort=latest', { auth: true });
    const items = data.content || [];
    if (!items.length) {
      body.innerHTML = '<div class="empty-state">저장한 코스가 아직 없어요</div>';
      return;
    }
    body.innerHTML = '';
    items.forEach((r) => {
      const el = document.createElement('div');
      el.className = 'card';
      el.style.cursor = 'pointer';
      el.innerHTML = `
        <strong>${escapeHtml(r.customName || r.aiTitle)}</strong> ${r.isFavorite ? '⭐' : ''}
        <div class="badge">${r.totalDistanceKm}km · ${r.estimatedDurationMin}분 · ${r.intensityLevel}</div>
      `;
      el.addEventListener('click', async () => {
        try {
          const full = await apiFetch(`/routes/${r.recommendedRouteId}`);
          state.lastRecommend = full;
          navigate('route-result');
        } catch (e) {
          alert('코스를 불러오지 못했어요: ' + e.message);
        }
      });
      body.appendChild(el);
    });
  } catch (e) {
    body.innerHTML = `<div class="error-banner">${escapeHtml(e.message)}</div>`;
  }
}
