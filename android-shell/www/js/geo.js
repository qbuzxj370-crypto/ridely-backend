// 좌표 계산 유틸. 기기 GPS로 얻은 위치는 서버로 보내지 않고 이런 계산을 전부 폰 안에서 한다
// (docs/shared/0918/LOCATION_PRIVACY_ARCHITECTURE.md).

/**
 * 현재 위치를 한 번 읽는다. 결과 좌표는 서버로 보내지 않는다.
 * 실패하면 GeolocationPositionError(code 1 권한 거부, 2 위치 불가, 3 시간 초과)로 reject한다.
 */
export function getCurrentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('이 기기에서는 위치 기능을 쓸 수 없어요'));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 30000, // 30초 안에 읽은 위치는 재사용 — 연타로 GPS를 매번 새로 켜지 않는다
    });
  });
}

/** 위치 읽기 실패를 사용자에게 보여줄 문구로 바꾼다 */
export function describeGeoError(e) {
  if (e && e.code === 1) return '위치 권한이 꺼져 있어요. 설정에서 위치를 허용해주세요';
  if (e && e.code === 2) return '현재 위치를 알 수 없어요. GPS 신호가 잡히는 곳에서 다시 시도해주세요';
  if (e && e.code === 3) return '위치를 확인하는 데 너무 오래 걸려요. 다시 시도해주세요';
  return (e && e.message) || '위치를 확인하지 못했어요';
}

/** 두 좌표 사이의 거리(m). 구면 거리(haversine) */
export function haversineM(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}
