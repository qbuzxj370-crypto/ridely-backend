import { apiFetch, newIdempotencyKey } from '../api.js';
import { navigate } from '../router.js';
import { isLoggedIn } from '../auth.js';
import state from '../state.js';

// 직전에 실패한 요청의 { key, bodyJson } 스냅샷. FRONTEND_GUIDE.md 규칙: "실패해서 재시도할
// 때만" 같은 Idempotency-Key를 재사용해야 한다 — 새로 추천받기나 입력을 바꾼 뒤의 제출은
// 새 키를 써야 한다. 그래서 body가 직전 실패 시도와 완전히 같을 때만 키를 재사용한다.
let lastFailedRequest = null;

export function render(container) {
  // 이전 방문에서 고른 출발지/도착지가 그대로 남아있으면, 화면엔 "선택됨" 배지가 안 보이는데
  // (새로 그려진 HTML이라 기본 숨김) 실제로는 재검색 없이 그 좌표로 제출돼버린다. 화면에
  // 보이는 것과 실제로 쓰일 값을 맞추기 위해 매번 새로 들어올 때 비워둔다.
  state.selectedStart = null;
  state.selectedEnd = null;
  lastFailedRequest = null;

  wireSearch(container, 'start');
  wireSearch(container, 'end');

  container.querySelector('#rp-use-location').addEventListener('click', () => useCurrentLocation(container));
  container.querySelector('#rp-submit').addEventListener('click', () => submit(container));

  applyAvoidCheckboxForLoginState(container);
}

/**
 * 회피 체크박스는 비회원 전용이다 — 회원은 백엔드가 이 체크박스(X-Ridely-Avoid-Danger-Zones
 * 헤더)를 무시하고 마이페이지 설정값만 따른다(RouteController.java 주석 참고). 로그인 상태에서
 * 체크박스를 그대로 두면 마이페이지 설정과 다르게 조작할 수 있어서 시연에서 바로 티가 난다.
 * 로그인 상태면 실제로 적용될 마이페이지 설정값을 그대로 보여주고 잠근다.
 */
async function applyAvoidCheckboxForLoginState(container) {
  const checkbox = container.querySelector('#rp-avoid');
  const hint = container.querySelector('#rp-avoid-hint');
  if (!isLoggedIn()) return;

  checkbox.disabled = true;
  try {
    const settings = await apiFetch('/users/me/settings', { auth: true });
    checkbox.checked = !!settings.avoidDangerZones;
    hint.textContent = '로그인 상태에서는 마이페이지 설정을 따라요 (마이페이지에서 바꿀 수 있어요)';
  } catch (e) {
    hint.textContent = '마이페이지 설정을 불러오지 못했어요 — 저장된 설정대로 적용됩니다';
  }
  hint.style.display = 'block';
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

  // 회원은 백엔드가 이 헤더를 무시하고 마이페이지 설정을 쓴다 — 굳이 보내서 착각을 남기지 않는다.
  const avoid = container.querySelector('#rp-avoid').checked;
  const headers = !isLoggedIn() && avoid ? { 'X-Ridely-Avoid-Danger-Zones': 'true' } : {};

  submitBtn.textContent = '코스를 만드는 중... (최대 15초)';

  // 직전 실패 시도랑 요청 내용이 완전히 같으면(=같은 입력으로 재시도) 그 키를 재사용하고,
  // 아니면(첫 시도 또는 입력을 바꿈) 새 키를 만든다.
  const bodyJson = JSON.stringify(body);
  const idempotencyKey =
    lastFailedRequest && lastFailedRequest.bodyJson === bodyJson
      ? lastFailedRequest.key
      : newIdempotencyKey();

  try {
    const data = await apiFetch('/routes/recommend', {
      method: 'POST',
      auth: true,
      body,
      headers,
      idempotencyKey,
    });
    lastFailedRequest = null; // 성공했으니 다음 클릭(다시 추천받기 등)은 완전히 새 요청으로 취급
    state.lastRecommend = data;
    navigate('route-result');
  } catch (e) {
    lastFailedRequest = { key: idempotencyKey, bodyJson };
    errorBox.innerHTML = `<div class="error-banner">${e.message}</div>`;
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = '코스 추천 받기';
  }
}
