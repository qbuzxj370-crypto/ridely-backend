// 화면 간 공유 상태. SPA라 페이지가 바뀌어도 메모리에 남는다(라우터가 새로고침을 안 하므로).
const state = {
  lastRecommend: null,       // 최근 코스 추천 응답 (route-plan -> route-result 전달용)
  selectedStart: null,       // { lat, lng, placeName } 검색에서 고른 출발지
  selectedEnd: null,
  pendingAfterLogin: null,   // 로그인 유도 후 되돌아갈 라우트
};

export default state;
