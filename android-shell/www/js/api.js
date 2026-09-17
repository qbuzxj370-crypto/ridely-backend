import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token-store.js';

// adb reverse tcp:8080 tcp:8080 로 기기의 localhost:8080을 PC 백엔드로 연결한다 (실기기 개발용).
// LAN IP 직접 연결을 잠깐 시도했었는데, 실패 원인이 SecurityConfig의 CORS 처리 위치였던 게
// 밝혀져서(2026-09-17) 원래대로 되돌렸다 — adb reverse가 원인이 아니었다.
export const API_BASE = 'http://localhost:8080/api/v1';

export class ApiError extends Error {
  constructor(code, message, details, httpStatus) {
    super(message);
    this.code = code;
    this.details = details;
    this.httpStatus = httpStatus;
  }
}

export function newIdempotencyKey() {
  return crypto.randomUUID();
}

let refreshInFlight = null;

async function rawRefresh() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new ApiError('COMMON-002', '로그인이 필요합니다', null, 401);

  const res = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  const body = await res.json();
  if (!body.success) {
    clearTokens();
    throw new ApiError(body.error?.code, body.error?.message, body.error?.details, res.status);
  }
  setTokens(body.data.accessToken, body.data.refreshToken);
  return body.data.accessToken;
}

function refreshOnce() {
  if (!refreshInFlight) {
    refreshInFlight = rawRefresh().finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

/**
 * @param {string} path  '/routes/recommend' 처럼 /api/v1 뒤부터
 * @param {object} options fetch 옵션 + { auth: true, idempotencyKey }
 */
export async function apiFetch(path, options = {}) {
  const { auth, idempotencyKey, headers, body, ...rest } = options;

  const doFetch = async () => {
    const finalHeaders = { ...(headers || {}) };
    if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
    if (idempotencyKey) finalHeaders['Idempotency-Key'] = idempotencyKey;
    const token = getAccessToken();
    if (auth && token) finalHeaders['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${API_BASE}${path}`, {
      ...rest,
      headers: finalHeaders,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    // 204 No Content
    if (res.status === 204) return { res, body: { success: true, data: null, error: null } };
    const json = await res.json();
    return { res, body: json };
  };

  let { res, body: json } = await doFetch();

  if (!json.success && json.error?.code === 'AUTH-301' && auth) {
    // 액세스 토큰 만료 -> 재발급 후 원 요청 1회 재시도 (동시 요청은 refreshOnce가 하나로 합침)
    await refreshOnce();
    ({ res, body: json } = await doFetch());
  }

  if (!json.success) {
    if (json.error?.code === 'AUTH-302' || json.error?.code === 'COMMON-002') {
      clearTokens();
    }
    throw new ApiError(json.error?.code, json.error?.message, json.error?.details, res.status);
  }

  return json.data;
}
