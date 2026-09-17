import { initRouter } from './router.js';

function onDeviceReady() {
  console.log('deviceready, platform=' + (window.cordova ? cordova.platformId : 'browser'));
  initRouter();
}

document.addEventListener('deviceready', onDeviceReady, false);
