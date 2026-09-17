// 해시 라우터. 페이지 마크업(pages/*.html)을 fetch로 불러와 #app에 넣고,
// 같은 이름의 js/pages/*.js 모듈의 render(container, params)를 호출한다.
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

let currentCleanup = null;

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
    container.innerHTML = `<div class="screen"><div class="error-banner">화면을 불러오지 못했어요: ${e.message}</div></div>`;
  }

  updateTabbar(route);
  container.scrollTop = 0;
}

function updateTabbar(route) {
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.route === route);
  });
  document.getElementById('tabbar').style.display = TAB_ROUTES.includes(route) ? 'flex' : 'none';
}

export function navigate(route, queryObj) {
  const qs = queryObj ? '?' + new URLSearchParams(queryObj).toString() : '';
  window.location.hash = `#/${route}${qs}`;
}

export function initRouter() {
  window.addEventListener('hashchange', renderRoute);
  document.addEventListener(
    'backbutton',
    (e) => {
      e.preventDefault();
      const { route } = parseHash();
      if (route === 'home') {
        if (window.navigator.app) window.navigator.app.exitApp();
      } else {
        window.history.back();
      }
    },
    false
  );
  renderRoute();
}
