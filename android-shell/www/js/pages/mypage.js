import { apiFetch } from '../api.js';
import { escapeHtml } from '../dom.js';
import { requireLoginOrRedirect, logout } from '../auth.js';
import { navigate } from '../router.js';
import { getSummary } from '../ride-storage.js';

export function render(container) {
  if (!requireLoginOrRedirect('mypage')) return;
  loadSummary(container);
  loadSettings(container);

  container.querySelector('#mp-save-settings').addEventListener('click', () => saveSettings(container));
  container.querySelector('#mp-logout').addEventListener('click', async () => {
    await logout();
    navigate('home');
  });
}

// 서버 GET /riding-sessions/summary 대신 로컬 기록을 직접 합산한다
// (docs/shared/0918/LOCATION_PRIVACY_ARCHITECTURE.md) — 라이딩 기록 자체가 서버에 없다.
function loadSummary(container) {
  const el = container.querySelector('#mp-summary');
  const s = getSummary();
  el.innerHTML = `
    <strong>누적 라이딩</strong>
    <div>총 ${s.totalRideCount}회 · ${s.totalDistanceKm}km</div>
    <div>${s.avgSpeedKmh != null ? '평균 ' + s.avgSpeedKmh + 'km/h' : '평균 속도 -'}</div>
  `;
}

async function loadSettings(container) {
  try {
    const settings = await apiFetch('/users/me/settings', { auth: true });
    container.querySelector('#mp-conv').value = settings.defaultPriorityConvenience;
    container.querySelector('#mp-ex').value = settings.defaultPriorityExercise;
    container.querySelector('#mp-sc').value = settings.defaultPriorityScenery;
    container.querySelector('#mp-avoid').checked = !!settings.avoidDangerZones;
  } catch (e) {
    container.querySelector('#mp-settings-error').innerHTML = `<div class="error-banner">${escapeHtml(e.message)}</div>`;
  }
}

async function saveSettings(container) {
  const errorBox = container.querySelector('#mp-settings-error');
  errorBox.innerHTML = '';
  const conv = parseFloat(container.querySelector('#mp-conv').value) || 0;
  const ex = parseFloat(container.querySelector('#mp-ex').value) || 0;
  const sc = Math.round((1 - conv - ex) * 100) / 100;
  if (sc < 0) {
    errorBox.innerHTML = '<div class="error-banner">편의+운동 합이 1을 넘을 수 없어요</div>';
    return;
  }
  try {
    await apiFetch('/users/me/settings', {
      method: 'PATCH',
      auth: true,
      body: {
        defaultPriorityConvenience: conv,
        defaultPriorityExercise: ex,
        defaultPriorityScenery: sc,
        avoidDangerZones: container.querySelector('#mp-avoid').checked,
      },
    });
    alert('저장했어요');
  } catch (e) {
    errorBox.innerHTML = `<div class="error-banner">${escapeHtml(e.message)}</div>`;
  }
}
