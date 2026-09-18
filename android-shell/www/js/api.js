import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token-store.js';

// 실배포 백엔드(EC2, CloudFront 경유). adb reverse+localhost:8080 조합은 로컬 개발용으로
// 되돌릴 때 이 줄만 원복하면 된다.
export const API_BASE = 'https://d2ym1ymgumwyg8.cloudfront.net/api/v1';

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

/**
 * 응답 본문을 JSON으로 읽는다. 백엔드가 죽어있거나 앞단(프록시 등)이 502를 HTML로
 * 내려주면 res.json()이 SyntaxError를 던지는데, 그걸 그대로 두면 화면에
 * "Unexpected token '<' ..." 같은 원문이 그대로 찍힌다. ApiError로 바꿔서 던진다.
 */
async function parseJsonSafely(res) {
  try {
    return await res.json();
  } catch (e) {
    throw new ApiError('COMMON-500', '서버와 통신하지 못했어요', null, res.status);
  }
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
  const body = await parseJsonSafely(res);
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
    const json = await parseJsonSafely(res);
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
