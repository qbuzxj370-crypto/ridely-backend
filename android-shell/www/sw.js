// PWA 설치 가능 조건(매니페스트 + 서비스워커) 충족용 — 오프라인 지원은 범위 밖이라
// 캐싱 없이 그냥 네트워크로 그대로 통과시킨다(docs/shared/0918/MOBILE_WEB_FALLBACK_PLAN.md).
self.addEventListener('fetch', (event) => {
  event.respondWith(fetch(event.request));
});
