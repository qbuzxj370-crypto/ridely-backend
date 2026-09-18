import { login, signup } from '../auth.js';
import { navigate } from '../router.js';

export function render(container, params) {
  const returnRoute = params.get('return') || 'home';
  let mode = 'login';

  const title = container.querySelector('#auth-title');
  const errorBox = container.querySelector('#auth-error');
  const nicknameField = container.querySelector('#auth-nickname-field');
  const consentField = container.querySelector('#auth-location-consent-field');
  const consentCheckbox = container.querySelector('#auth-location-consent');
  const submitBtn = container.querySelector('#auth-submit');
  const toggleBtn = container.querySelector('#auth-toggle');

  function setMode(next) {
    mode = next;
    const isSignup = mode === 'signup';
    title.textContent = isSignup ? '회원가입' : '로그인';
    nicknameField.style.display = isSignup ? 'block' : 'none';
    consentField.style.display = isSignup ? 'block' : 'none';
    submitBtn.textContent = isSignup ? '가입하기' : '로그인';
    toggleBtn.textContent = isSignup ? '이미 계정이 있으신가요? 로그인' : '계정이 없으신가요? 회원가입';
    errorBox.innerHTML = '';
  }

  toggleBtn.addEventListener('click', () => setMode(mode === 'login' ? 'signup' : 'login'));

  submitBtn.addEventListener('click', async () => {
    // 연타 방지 — 특히 회원가입은 로그인까지 이어서 두 번 호출이라 더 걸린다. 안 잠그면
    // 연타 시 동시에 두 번 가입 요청이 나가서 하나는 성공하고 하나는 AUTH-101로 실패하는데,
    // 실제로는 성공했는데도 에러 문구가 같이 뜨는 것처럼 보인다.
    if (submitBtn.disabled) return;
    submitBtn.disabled = true;

    const loginId = container.querySelector('#auth-loginId').value.trim();
    const password = container.querySelector('#auth-password').value;
    errorBox.innerHTML = '';

    // 위치정보 수집 동의는 서버로 전송하지 않는다 — 순전히 클라이언트에서 GPS를 쓰기 전에
    // 사용자에게 고지하고 확인받는 게 목적이라, 이 화면(가입)에서만 막으면 된다.
    if (mode === 'signup' && !consentCheckbox.checked) {
      errorBox.innerHTML = '<div class="error-banner">위치정보 수집·이용에 동의해야 가입할 수 있어요</div>';
      submitBtn.disabled = false;
      return;
    }

    try {
      if (mode === 'signup') {
        const nickname = container.querySelector('#auth-nickname').value.trim();
        await signup(loginId, password, nickname);
        await login(loginId, password);
      } else {
        await login(loginId, password);
      }
      navigate(returnRoute);
    } catch (e) {
      errorBox.innerHTML = `<div class="error-banner">${e.message || '처리 중 오류가 발생했어요'}</div>`;
      submitBtn.disabled = false;
    }
  });

  setMode('login');
}
