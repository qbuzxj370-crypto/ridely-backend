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

// 탭바가 없는 화면 중 헤더에 뒤로가기가 필요한 라우트.
const HEADER_BACK_ROUTES = ['auth', 'route-plan', 'route-result', 'riding-history'];

let currentCleanup = null;

// 자체 네비게이션 스택. window.history.back()에 의존하지 않는다 -
// 이 WebView 환경에서 history.back() 뒤에 hashchange가 안정적으로 안 붙어서
// (onBackInvoked는 반복 발생하는데 화면은 그대로 있는 증상), 뒤로가기를
// "이전 해시로 되돌리기"가 아니라 "직전에 스택에 쌓인 라우트로 직접 이동"으로 바꾼다.
const navStack = [];
const tabContainers = new Map();
const tabLifecycles = new Map();
let activeTabRoute = null;
let renderSequence = 0;

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

function playEntryAnimation(screenEl) {
  if (!screenEl) return;
  screenEl.style.animation = 'none';
  void screenEl.offsetWidth; // 애니메이션을 처음부터 다시 재생한다.
  screenEl.style.animation = '';
}

async function renderRoute() {
  const sequence = ++renderSequence;
  const { route, params } = parseHash();
  const def = routes[route] || routes.home;
  const isTab = TAB_ROUTES.includes(route);
  const app = document.getElementById('app');
  const tabViews = document.getElementById('tab-views');
  const pageView = document.getElementById('page-view');

  if (activeTabRoute && activeTabRoute !== route) {
    const prevLifecycle = tabLifecycles.get(activeTabRoute);
    if (prevLifecycle && prevLifecycle.onHide) {
      try { prevLifecycle.onHide(); } catch (e) { console.error(e); }
    }
    const prevWrapper = tabContainers.get(activeTabRoute);
    if (prevWrapper) prevWrapper.style.display = 'none';
    activeTabRoute = null;
  }

  if (isTab) {
    if (currentCleanup) {
      try { currentCleanup(); } catch (e) { console.error(e); }
      currentCleanup = null;
    }
    pageView.style.display = 'none';
    tabViews.style.display = '';

    let wrapper = tabContainers.get(route);
    if (!wrapper) {
      wrapper = document.createElement('div');
      tabViews.appendChild(wrapper);
      try {
        const htmlText = await fetch(def.html).then((r) => r.text());
        if (sequence !== renderSequence) { wrapper.remove(); return; }
        wrapper.innerHTML = htmlText;
        const mod = await import(def.mod);
        if (sequence !== renderSequence) { wrapper.remove(); return; }
        const lifecycle = mod.render(wrapper, params);
        if (lifecycle === null) {
          wrapper.remove();
          wrapper = null;
        } else {
          tabContainers.set(route, wrapper);
          tabLifecycles.set(route, lifecycle || {});
        }
      } catch (e) {
        if (sequence !== renderSequence) { wrapper.remove(); return; }
        console.error('page render failed', route, e);
        wrapper.remove();
        wrapper = null;
      }
    } else {
      wrapper.style.display = '';
      const lifecycle = tabLifecycles.get(route);
      if (lifecycle && lifecycle.onShow) {
        try {
          if (lifecycle.onShow() === false) {
            wrapper.style.display = 'none';
            wrapper = null;
          }
        } catch (e) { console.error(e); }
      }
    }

    if (wrapper) {
      wrapper.style.display = '';
      activeTabRoute = route;
      playEntryAnimation(wrapper.firstElementChild);
    }
  } else {
    tabViews.style.display = 'none';
    pageView.style.display = '';

    if (currentCleanup) {
      try { currentCleanup(); } catch (e) { console.error(e); }
      currentCleanup = null;
    }
    try {
      const htmlText = await fetch(def.html).then((r) => r.text());
      if (sequence !== renderSequence) return;
      pageView.innerHTML = htmlText;
      const mod = await import(def.mod);
      if (sequence !== renderSequence) return;
      const result = mod.render(pageView, params);
      if (typeof result === 'function') currentCleanup = result;
    } catch (e) {
      if (sequence !== renderSequence) return;
      console.error('page render failed', route, e);
      pageView.innerHTML = `<div class="screen"><div class="error-banner">화면을 불러오지 못했어요: ${escapeHtml(e.message)}</div></div>`;
    }
  }

  updateTabbar(route);
  updateHeaderBack(route);
  app.scrollTop = 0;
}

function updateTabbar(route) {
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.route === route);
  });
  document.getElementById('tabbar').style.display = TAB_ROUTES.includes(route) ? 'flex' : 'none';
  const indicator = document.getElementById('tab-indicator');
  if (indicator) indicator.style.transform = `translateX(${TAB_ROUTES.indexOf(route) * 100}%)`;
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
