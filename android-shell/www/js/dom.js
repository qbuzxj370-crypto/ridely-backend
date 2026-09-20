// innerHTML 템플릿에 외부 문자열을 넣을 때 쓰는 이스케이프.
//
// 장소 검색 결과(Kakao)·저장한 코스 이름(사용자 입력)·AI가 쓴 제목·서버 오류 문구는 우리가
// 통제하지 못하는 문자열이다. 이걸 그대로 innerHTML에 넣으면 문자열 속 HTML이 실행된다 — 이 앱은
// 로그인 토큰을 localStorage에 두므로 한 군데라도 뚫리면 토큰이 새는 경로가 된다.
// 새로 만드는 화면은 textContent/DOM API를 우선 쓰고, 템플릿 문자열이 편할 때만 이걸 거친다.

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ESCAPES[c]);
}
