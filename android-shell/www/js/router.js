// 해시 라우터. 페이지 마크업(pages/*.html)을 fetch로 불러와 #app에 넣고,
// 같은 이름의 js/pages/*.js 모듈의 render(container, params)를 호출한다.
import { escapeHtml } from './dom.js';

const routes = {
  home: { html: 'pages/home.html', mod: './pages/home.js' },
  auth: { html: 'pages/auth.html', mod: './pages/auth.js' },
  'route-plan': { html: 'pages/route-plan.html', mod: './pages/route-plan.js' },
  'route-result': { html: 'pages/route-result.html', mod: './pages/route-result.js' },
  'saved-routes': { html: 'pages/saved-routes.html', mod: './pages/saved-routes.js' },
  riding: { html: 'pages/riding.html', mod: './pages/riding.js' },
  'riding-history': { html: 'pages/riding-history.html', mod: './pages/riding-history.js' },
  mypage: { html: 'pages/mypage.html', mod: './pages/mypage.js' },
};

const TAB_ROUTES = ['home', 'saved-routes', 'riding', 'mypage'];

// 탭바가 없는 화면 중 헤더에 뒤로가기가 필요한 라우트. 지금은 로그인/회원가입 화면뿐이다.
const HEADER_BACK_ROUTES = ['auth'];

let currentCleanup = null;

// 자체 네비게이션 스택. window.history.back()에 의존하지 않는다 -
// 이 WebView 환경에서 history.back() 뒤에 hashchange가 안정적으로 안 붙어서
// (onBackInvoked는 반복 발생하는데 화면은 그대로 있는 증상), 뒤로가기를
// "이전 해시로 되돌리기"가 아니라 "직전에 스택에 쌓인 라우트로 직접 이동"으로 바꾼다.
const navStack = [];

// 뒤로가기로 종료되는 지점(navStack 비었거나 홈)에서 한 번에 안 꺼지고
// 짧은 시간 안에 한 번 더 눌러야 꺼지게 한다 — 실수로 앱이 바로 닫히는 걸 막는다.
const EXIT_CONFIRM_WINDOW_MS = 2000;
let lastExitPressAt = 0;

function showExitToast() {
  let toast = document.getElementById('exit-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'exit-toast';
    toast.className = 'exit-toast';
    toast.textContent = '한 번 더 누르면 종료됩니다';
    document.body.appendChild(toast);
  }
  toast.classList.add('show');
  clearTimeout(showExitToast.hideTimer);
  showExitToast.hideTimer = setTimeout(() => toast.classList.remove('show'), EXIT_CONFIRM_WINDOW_MS);
}

function parseHash() {
  const raw = window.location.hash.replace(/^#\/?/, '');
  const [route, queryStr] = raw.split('?');
  return { route: route || 'home', params: new URLSearchParams(queryStr || '') };
}

async function renderRoute() {
  const { route, params } = parseHash();
  const def = routes[route] || routes.home;

  if (currentCleanup) {
    try { currentCleanup(); } catch (e) { console.error(e); }
    currentCleanup = null;
  }

  const container = document.getElementById('app');
  try {
    const htmlText = await fetch(def.html).then((r) => r.text());
    container.innerHTML = htmlText;
    const mod = await import(def.mod);
    const result = mod.render(container, params);
    if (typeof result === 'function') currentCleanup = result;
  } catch (e) {
    console.error('page render failed', route, e);
    container.innerHTML = `<div class="screen"><div class="error-banner">화면을 불러오지 못했어요: ${escapeHtml(e.message)}</div></div>`;
  }

  updateTabbar(route);
  updateHeaderBack(route);
  container.scrollTop = 0;
}

function updateTabbar(route) {
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.route === route);
  });
  document.getElementById('tabbar').style.display = TAB_ROUTES.includes(route) ? 'flex' : 'none';
}

function updateHeaderBack(route) {
  const btn = document.getElementById('header-back-btn');
  if (btn) btn.style.display = HEADER_BACK_ROUTES.includes(route) ? 'inline-block' : 'none';
}

/** 해시를 바꾸고 필요하면 직접 렌더한다. 스택은 건드리지 않는다 (뒤로가기 전용 경로) */
function goToHash(route, queryObj) {
  const qs = queryObj ? '?' + new URLSearchParams(queryObj).toString() : '';
  const hash = `#/${route}${qs}`;
  if (window.location.hash === hash) {
    // 해시가 안 바뀌면 hashchange가 안 붙어서 renderRoute가 안 불린다.
    renderRoute();
  } else {
    window.location.hash = hash;
  }
}

export function navigate(route, queryObj) {
  const { route: currentRoute } = parseHash();
  if (currentRoute !== route) navStack.push(currentRoute);
  goToHash(route, queryObj);
}

export function initRouter() {
  window.addEventListener('hashchange', renderRoute);
  // returnRoute(예: 'riding')로 보내면 아직 로그인 전이라 requireLoginOrRedirect가
  // 다시 이 화면으로 튕겨낸다 — 헤더 뒤로가기는 항상 홈으로 보낸다.
  document.getElementById('header-back-btn').addEventListener('click', () => navigate('home'));
  document.addEventListener(
    'backbutton',
    (e) => {
      e.preventDefault();
      const { route } = parseHash();
      if (navStack.length === 0 || route === 'home') {
        const now = Date.now();
        if (now - lastExitPressAt < EXIT_CONFIRM_WINDOW_MS) {
          if (window.navigator.app) window.navigator.app.exitApp();
          return;
        }
        lastExitPressAt = now;
        showExitToast();
        return;
      }
      // navigate()가 아니라 goToHash()를 쓴다 - navigate()를 쓰면 "뒤로 가기"
      // 자체가 다시 스택에 쌓여서, home<->A를 반복 왕복할 때 스택이 안 비워진다.
      const prevRoute = navStack.pop();
      goToHash(prevRoute);
    },
    false
  );
  renderRoute();
}
