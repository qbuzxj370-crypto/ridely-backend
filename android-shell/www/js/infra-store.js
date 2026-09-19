// 서비스 지역 인프라 전체를 폰에 저장해 두고, 기기 GPS로 「내 주변」을 폰 안에서 거른다.
//
// 왜 이렇게 하는가: 「내 주변 시설」을 서버에 물으면 요청에 내 위치가 실린다. 위치정보를 서버로
// 보내지 않는다는 원칙(docs/shared/0918/LOCATION_PRIVACY_ARCHITECTURE.md) 때문에, 서버에는
// 「전부 주세요」(GET /pois/all, 파라미터 없음)만 묻고 거리 계산은 여기서 한다.
//
// ⚠️ loadAllInfra()의 요청에는 좌표·반경 같은 위치 단서를 절대 붙이지 않는다. 서버가 무시하더라도
// 요청에 실려 나가면 원칙이 깨진다.
import { apiFetch } from './api.js';
import { haversineM } from './geo.js';

const STORAGE_KEY = 'ridely.infra.all';

// 폰에 저장한 목록을 이 시간 동안은 서버를 안 부르고 그대로 쓴다. 적재 데이터는 가끔만 바뀐다.
// 서버 응답의 Cache-Control(1시간)과는 별개다 — 그건 브라우저 HTTP 캐시, 이건 우리가 저장한 사본이다.
const FRESH_MS = 24 * 60 * 60 * 1000;

// 서버 반경 조회(/pois/nearby)가 종류마다 두던 상한과 같다. 따릉이가 목록을 채워 희소한 종류가
// 밀려나는 것을 막고, 반경이 넓을 때 지도에 찍는 마커 수도 제한한다.
const PER_TYPE_LIMIT = 100;

let memory = null;   // { savedAt, items } — 같은 세션에서 localStorage 파싱을 반복하지 않으려는 사본
let inflight = null; // 진행 중인 요청. 동시에 여러 번 불려도 한 번만 나간다

function readStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw);
    if (!value || typeof value.savedAt !== 'number' || !Array.isArray(value.items)) return null;
    return value;
  } catch (e) {
    return null; // 깨진 저장본은 없는 셈 친다 — 새로 받으면 덮어쓴다
  }
}

function writeStored(value) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch (e) {
    // 용량 초과·저장소 비활성 — 저장은 못 해도 이번 세션(memory)에서는 쓸 수 있다.
    // 데이터가 지금의 몇 배로 늘면 여기가 먼저 터진다: IndexedDB로 옮길 시점(FRONTEND_GUIDE.md).
  }
}

/**
 * 전체 인프라 목록. 저장본이 신선하면 그대로, 아니면 서버에서 받는다.
 * 서버가 실패해도 저장본이 있으면(오래됐더라도) 그걸 돌려준다 — 목록이 좀 낡은 것이 아예 안
 * 보이는 것보다 낫다. 그때 stale=true라 화면이 알릴 수 있다.
 *
 * @returns {Promise<{items: object[], savedAt: number, stale: boolean}>}
 */
export function loadAllInfra({ force = false } = {}) {
  if (inflight) return inflight;

  const cached = memory || readStored();
  if (cached && !force && Date.now() - cached.savedAt < FRESH_MS) {
    memory = cached;
    return Promise.resolve({ items: cached.items, savedAt: cached.savedAt, stale: false });
  }

  inflight = (async () => {
    try {
      const data = await apiFetch('/pois/all');
      const items = (data && data.items) || [];
      const fresh = { savedAt: Date.now(), items };
      // 빈 목록은 저장하지 않는다. 적재 전이거나 서버 이상일 수 있는데, 그걸 24시간 신선한
      // 사본으로 굳히면 데이터가 생겨도 하루 동안 아무것도 안 보인다.
      if (items.length > 0) {
        memory = fresh;
        writeStored(fresh);
      }
      return { items, savedAt: fresh.savedAt, stale: false };
    } catch (e) {
      if (cached) {
        memory = cached;
        return { items: cached.items, savedAt: cached.savedAt, stale: true };
      }
      throw e;
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

/**
 * 기준 좌표에서 반경 안의 항목을 가까운 순으로 돌려준다. 거리 계산은 전부 폰 안에서 한다.
 *
 * @param {string[]} types 남길 종류. ROUTE_FACILITY는 세부 종류(WATER 등)로 비교한다
 * @returns {object[]} 각 항목에 distanceM(반올림)·typeKey를 붙인 복사본. 종류마다 가까운 100개까지
 */
export function filterNearby(items, lat, lng, radiusM, types) {
  const wanted = new Set(types);
  const within = [];
  for (const item of items) {
    const typeKey = item.type === 'ROUTE_FACILITY' ? item.facilityType : item.type;
    if (!wanted.has(typeKey)) continue;
    const d = haversineM(lat, lng, item.lat, item.lng);
    if (d <= radiusM) within.push({ ...item, typeKey, distanceM: Math.round(d) });
  }
  within.sort((a, b) => a.distanceM - b.distanceM);

  const perType = {};
  return within.filter((item) => {
    perType[item.typeKey] = (perType[item.typeKey] || 0) + 1;
    return perType[item.typeKey] <= PER_TYPE_LIMIT;
  });
}

/** 테스트·개발용: 메모리와 저장본을 지운다 */
export function clearInfraCache() {
  memory = null;
  inflight = null;
  try { localStorage.removeItem(STORAGE_KEY); } catch (e) { /* 무시 */ }
}
