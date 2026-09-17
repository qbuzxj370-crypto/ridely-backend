import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token-store.js';

// adb reverse tcp:8080 tcp:8080 로 기기의 localhost:8080을 PC 백엔드로 연결한다 (실기기 개발용).
// LAN IP 직접 연결을 잠깐 시도했었는데, 실패 원인이 SecurityConfig의 CORS 처리 위치였던 게
// 밝혀져서(2026-09-17) 원래대로 되돌렸다 — adb reverse가 원인이 아니었다.
//
// ⚠️ 배포 주소로 바꿀 때는 이 값 하나만 고치면 끝나는 게 아니다. 아래 세 곳을 같이 바꿔야
// 한다 — 하나라도 빠지면 증상이 전부 "CORS 차단"으로 보여서 원인 찾기가 오래 걸린다:
//   1. 여기 API_BASE
//   2. www/index.html의 CSP <meta> 태그, connect-src에 있는 http://localhost:8080·
//      http://10.0.2.2:8080 (새 주소를 추가/교체)
//   3. EC2(배포 서버)의 CORS_ALLOWED_ORIGINS 환경변수 — application-prod.yml이 이 값으로
//      ridely.cors.allowed-origins를 통째로 덮어쓴다 (docs/shared/BACKEND_CHANGES.md 참고)
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
