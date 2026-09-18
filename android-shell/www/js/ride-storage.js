// 라이딩 세션을 서버로 절대 보내지 않고 기기에만 저장한다
// (docs/shared/0918/LOCATION_PRIVACY_ARCHITECTURE.md) — 라이딩 시작/종료를 위해 서버에
// 세션을 만들던 것(POST/PATCH /riding-sessions)을 걷어내고, 전 구간을 이 모듈로 대체했다.
// 대가: 기록이 이 기기에만 남는다 — 앱 삭제·기기 변경 시 사라지고 여러 기기 동기화도 안 된다.

const HISTORY_KEY = 'ridely.riding.history';
const MAX_SESSIONS = 200; // 무한정 쌓여 localStorage를 다 채우는 것 방지 — 오래된 것부터 버림

function readAll() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

function writeAll(sessions) {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(sessions));
  } catch (e) {
    // 저장 공간 부족 등 — 기록 하나 못 남기는 게 앱 중단보다 낫다
  }
}

export function newSessionId() {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `local-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * @param {object} session
 * @param {string} session.sessionId
 * @param {string|null} session.recommendedRouteId
 * @param {number} session.startedAt  epoch ms
 * @param {number} session.endedAt    epoch ms
 * @param {number} session.distanceKm
 * @param {number} session.avgSpeedKmh
 * @param {number} session.alertReceivedCount
 */
export function saveCompletedSession(session) {
  const sessions = readAll();
  sessions.unshift({ ...session, isCompleted: true });
  if (sessions.length > MAX_SESSIONS) sessions.length = MAX_SESSIONS;
  writeAll(sessions);
}

export function listSessions() {
  return readAll();
}

/**
 * 마이페이지 누적 통계용. 평균 속도는 세션별 avgSpeedKmh를 단순 평균하지 않는다 — 짧고 빠른
 * 라이딩 하나가 평균을 과도하게 끌어올리는 걸 막기 위해, 총거리/총시간(경과시간 합)으로 계산한다.
 */
export function getSummary() {
  const sessions = readAll();
  const totalRideCount = sessions.length;
  if (totalRideCount === 0) {
    return { totalRideCount: 0, totalDistanceKm: 0, avgSpeedKmh: null };
  }
  const totalDistanceKm = sessions.reduce((sum, s) => sum + (s.distanceKm || 0), 0);
  const totalHours = sessions.reduce((sum, s) => sum + Math.max(0, (s.endedAt - s.startedAt) / 3600000), 0);
  return {
    totalRideCount,
    totalDistanceKm: Math.round(totalDistanceKm * 100) / 100,
    avgSpeedKmh: totalHours > 0 ? Math.round((totalDistanceKm / totalHours) * 10) / 10 : null,
  };
}
