import { initRouter } from './router.js';

function onDeviceReady() {
  console.log('deviceready, platform=' + (window.cordova ? cordova.platformId : 'browser'));
  initRouter();
}

// cordova.js는 index.html의 <script> 태그가 먼저 실행되므로(module 스크립트보다 앞서 파싱·
// 동기 실행됨) 이 시점엔 window.cordova 존재 여부가 이미 확정돼 있다. 실제 Cordova 빌드가
// 아니라 일반 웹(모바일 Chrome 등)에서 열리면 cordova.js가 없어 deviceready가 영영 안
// 오므로, 이 경우엔 바로 시작한다(docs/shared/0918/MOBILE_WEB_FALLBACK_PLAN.md).
if (window.cordova) {
  document.addEventListener('deviceready', onDeviceReady, false);
} else {
  onDeviceReady();
  // 서비스워커는 PWA 설치 가능 조건(매니페스트+서비스워커) 충족용이다 — Cordova WebView는
  // 이미 설치된 앱처럼 동작하므로 등록할 이유가 없어 웹 분기에서만 시도한다.
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch((e) => console.error('sw register failed', e));
  }
}
