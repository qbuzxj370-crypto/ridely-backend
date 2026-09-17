import { login, signup } from '../auth.js';
import { navigate } from '../router.js';

export function render(container, params) {
  const returnRoute = params.get('return') || 'home';
  let mode = 'login';

  const title = container.querySelector('#auth-title');
  const errorBox = container.querySelector('#auth-error');
  const nicknameField = container.querySelector('#auth-nickname-field');
  const submitBtn = container.querySelector('#auth-submit');
  const toggleBtn = container.querySelector('#auth-toggle');

  function setMode(next) {
    mode = next;
    const isSignup = mode === 'signup';
    title.textContent = isSignup ? '회원가입' : '로그인';
    nicknameField.style.display = isSignup ? 'block' : 'none';
    submitBtn.textContent = isSignup ? '가입하기' : '로그인';
    toggleBtn.textContent = isSignup ? '이미 계정이 있으신가요? 로그인' : '계정이 없으신가요? 회원가입';
    errorBox.innerHTML = '';
  }

  toggleBtn.addEventListener('click', () => setMode(mode === 'login' ? 'signup' : 'login'));

  submitBtn.addEventListener('click', async () => {
    const loginId = container.querySelector('#auth-loginId').value.trim();
    const password = container.querySelector('#auth-password').value;
    errorBox.innerHTML = '';

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
    }
  });

  setMode('login');
}
