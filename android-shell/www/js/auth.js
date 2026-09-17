import { apiFetch } from './api.js';
import { setTokens, clearTokens, isLoggedIn } from './token-store.js';

export { isLoggedIn };

export async function login(loginId, password) {
  const data = await apiFetch('/auth/login', {
    method: 'POST',
    body: { loginId, password },
  });
  setTokens(data.accessToken, data.refreshToken);
  return data;
}

export async function signup(loginId, password, nickname) {
  return apiFetch('/auth/signup', {
    method: 'POST',
    body: { loginId, password, nickname },
  });
}

export async function logout() {
  const { getRefreshToken } = await import('./token-store.js');
  const refreshToken = getRefreshToken();
  try {
    await apiFetch('/auth/logout', { method: 'POST', body: { refreshToken } });
  } catch (e) {
    // 가이드: 이미 폐기된 토큰이어도 204. 응답과 무관하게 로컬 토큰은 지운다.
  }
  clearTokens();
}

/**
 * 보호된 동작을 시도했는데 미로그인이면 로그인 화면으로 보내고, 성공 후 원래 라우트로 돌아온다.
 */
export function requireLoginOrRedirect(returnRoute) {
  if (isLoggedIn()) return true;
  window.location.hash = `#/auth?return=${encodeURIComponent(returnRoute)}`;
  return false;
}
