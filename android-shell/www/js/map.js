// Kakao Map JS SDK 초기화·마커·경로·사고다발지 폴리곤 헬퍼.
import { KAKAO_JS_KEY } from './config.local.js';

let loadPromise = null;

function loadKakaoSdk() {
  if (window.kakao && window.kakao.maps && window.kakao.maps.Map) return Promise.resolve();
  if (loadPromise) return loadPromise;
  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&autoload=false&libraries=services`;
    script.onload = () => window.kakao.maps.load(resolve);
    script.onerror = (e) => {
      // 실패한 프로미스를 캐시에 남겨두면, 원인(네트워크 순간 끊김 등)이 사라진 뒤에도
      // 이 세션 안에서는 지도가 영원히 안 뜬다 — 다음 시도가 새로 로드해볼 수 있게 지운다.
      loadPromise = null;
      reject(new Error('카카오맵 SDK 로드 실패: ' + e));
    };
    document.head.appendChild(script);
  });
  return loadPromise;
}

export async function createMap(containerId, { lat, lng, level = 4 }) {
  await loadKakaoSdk();
  const container = document.getElementById(containerId);
  return new kakao.maps.Map(container, {
    center: new kakao.maps.LatLng(lat, lng),
    level,
  });
}

export function addMarker(map, lat, lng, title) {
  const marker = new kakao.maps.Marker({
    position: new kakao.maps.LatLng(lat, lng),
    title: title || '',
  });
  marker.setMap(map);
  return marker;
}

export function drawPolyline(map, coordinates) {
  // coordinates: [[lng, lat], ...] (GeoJSON 순서)
  const path = coordinates.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
  const polyline = new kakao.maps.Polyline({
    path,
    strokeWeight: 5,
    strokeColor: '#1E7B6C',
    strokeOpacity: 0.9,
    strokeStyle: 'solid',
  });
  polyline.setMap(map);
  return polyline;
}

export function drawDangerZonePolygon(map, coordinates) {
  // coordinates: [[lng, lat], ...] (폴리곤 외곽선, GeoJSON 순서)
  const path = coordinates.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
  const polygon = new kakao.maps.Polygon({
    path,
    strokeWeight: 2,
    strokeColor: '#E5484D',
    strokeOpacity: 0.8,
    fillColor: '#E5484D',
    fillOpacity: 0.35,
  });
  polygon.setMap(map);
  return polygon;
}

// 목록에서 항목을 눌렀을 때 지도를 그 지점으로 부드럽게 옮긴다. setCenter는 순간이동이라
// 어디로 갔는지 놓치기 쉽다. 마커를 줌 변경 없이 화면 중앙으로 가져오는 용도다.
export function panTo(map, lat, lng) {
  map.panTo(new kakao.maps.LatLng(lat, lng));
}

export function fitBounds(map, latLngList) {
  const bounds = new kakao.maps.LatLngBounds();
  latLngList.forEach(([lat, lng]) => bounds.extend(new kakao.maps.LatLng(lat, lng)));
  map.setBounds(bounds);
}
