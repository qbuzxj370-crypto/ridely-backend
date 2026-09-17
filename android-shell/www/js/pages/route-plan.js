import { apiFetch, newIdempotencyKey } from '../api.js';
import { navigate } from '../router.js';
import state from '../state.js';

export function render(container) {
  wireSearch(container, 'start');
  wireSearch(container, 'end');

  container.querySelector('#rp-use-location').addEventListener('click', () => useCurrentLocation(container));
  container.querySelector('#rp-submit').addEventListener('click', () => submit(container));
}

function wireSearch(container, which) {
  const btn = container.querySelector(`#rp-${which}-search`);
  const input = container.querySelector(`#rp-${which}-query`);
  const resultsBox = container.querySelector(`#rp-${which}-results`);
  const selectedBox = container.querySelector(`#rp-${which}-selected`);

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
        if (place.inServiceArea) {
          row.addEventListener('click', () => {
            const picked = { lat: place.lat, lng: place.lng, placeName: place.placeName };
            if (which === 'start') state.selectedStart = picked;
            else state.selectedEnd = picked;
            selectedBox.textContent = `선택됨: ${place.placeName}`;
            selectedBox.style.display = 'inline-block';
            resultsBox.innerHTML = '';
            input.value = place.placeName;
          });
        }
        resultsBox.appendChild(row);
      });
    } catch (e) {
      resultsBox.innerHTML = `<div class="error-banner">${e.message}</div>`;
    }
  });
}

function useCurrentLocation(container) {
  const selectedBox = container.querySelector('#rp-start-selected');
  if (!navigator.geolocation) {
    container.querySelector('#rp-error').innerHTML = '<div class="error-banner">이 기기에서는 위치 기능을 쓸 수 없어요</div>';
    return;
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      state.selectedStart = { lat: pos.coords.latitude, lng: pos.coords.longitude, placeName: '현재 위치' };
      selectedBox.textContent = '선택됨: 현재 위치';
      selectedBox.style.display = 'inline-block';
    },
    (err) => {
      container.querySelector('#rp-error').innerHTML = `<div class="error-banner">위치를 가져오지 못했어요: ${err.message}</div>`;
    },
    { enableHighAccuracy: true, timeout: 10000 }
  );
}

async function submit(container) {
  const submitBtn = container.querySelector('#rp-submit');
  // 연타 방지 — 이 요청은 LLM을 불러 최대 15초까지 걸린다. 버튼을 여기서 바로 잠그지 않으면
  // 응답을 기다리는 동안 여러 번 눌러 같은 요청이 겹쳐 나가고, 서버 스레드가 붙잡힌 채
  // 뒤섞여 타임아웃·연결 오류로 보인다 (실기기에서 실제로 3초에 13번 나간 걸 확인했다).
  if (submitBtn.disabled) return;
  submitBtn.disabled = true;

  const errorBox = container.querySelector('#rp-error');
  errorBox.innerHTML = '';

  if (!state.selectedStart) {
    submitBtn.disabled = false;
    errorBox.innerHTML = '<div class="error-banner">출발지를 먼저 선택해주세요</div>';
    return;
  }

  const targetDistanceKm = parseFloat(container.querySelector('#rp-distance').value);
  let conv = parseFloat(container.querySelector('#rp-conv').value) || 0;
  let ex = parseFloat(container.querySelector('#rp-ex').value) || 0;
  // 풍경 값은 나머지로 자동 보정한다 — 합이 정확히 1.00이어야 하므로 (ROUTE-001) 세 슬라이더를
  // 각각 손으로 맞추게 두지 않는다.
  let sc = Math.round((1 - conv - ex) * 100) / 100;
  if (sc < 0) {
    submitBtn.disabled = false;
    errorBox.innerHTML = '<div class="error-banner">편의+운동 합이 1을 넘을 수 없어요</div>';
    return;
  }

  const body = {
    startLat: state.selectedStart.lat,
    startLng: state.selectedStart.lng,
    targetDistanceKm,
    priorityConvenience: conv,
    priorityExercise: ex,
    priorityScenery: sc,
  };
  if (state.selectedEnd) {
    body.endLat = state.selectedEnd.lat;
    body.endLng = state.selectedEnd.lng;
  }

  const avoid = container.querySelector('#rp-avoid').checked;
  const headers = avoid ? { 'X-Ridely-Avoid-Danger-Zones': 'true' } : {};

  submitBtn.textContent = '코스를 만드는 중... (최대 15초)';

  try {
    const data = await apiFetch('/routes/recommend', {
      method: 'POST',
      auth: true,
      body,
      headers,
      idempotencyKey: newIdempotencyKey(),
    });
    state.lastRecommend = data;
    navigate('route-result');
  } catch (e) {
    errorBox.innerHTML = `<div class="error-banner">${e.message}</div>`;
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = '코스 추천 받기';
  }
}
