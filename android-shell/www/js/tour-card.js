// 관광지 상세 카드. 추천 결과의 경유지 상세와 홈 지도의 관광지 마커 상세가 같은 카드를 쓴다.
import { apiFetch } from './api.js';

// 관광지 정보는 적재 데이터라 세션 안에서 바뀌지 않는다 — 같은 항목을 다시 열 때 서버를 또
// 부르지 않는다. 동시에 같은 번호를 두 번 요청해도 한 번만 나가게 진행 중인 요청도 공유한다.
const cache = new Map();
const inflight = new Map();

/** GET /tours/{id}. 실패는 캐시하지 않으므로 다시 시도하면 새로 요청한다 */
export function fetchTourDetail(id) {
  if (cache.has(id)) return Promise.resolve(cache.get(id));
  if (inflight.has(id)) return inflight.get(id);
  const request = apiFetch(`/tours/${encodeURIComponent(id)}`)
    .then((tour) => {
      cache.set(id, tour);
      return tour;
    })
    .finally(() => inflight.delete(id));
  inflight.set(id, request);
  return request;
}

/**
 * TourAPI 문자열은 원본이 HTML 조각이다. 개요에는 <br />이 섞여 있고 &amp; 같은 엔티티도
 * 온다. textContent에 그대로 넣으면 태그가 글자로 찍히므로, <br>만 줄바꿈으로 살리고
 * 나머지는 DOMParser(스크립트 실행·이미지 로드가 없는 비활성 문서)로 벗겨서 글자만 남긴다.
 */
function toPlainText(html) {
  const withBreaks = String(html).replace(/<br\s*\/?>/gi, '\n');
  const doc = new DOMParser().parseFromString(withBreaks, 'text/html');
  // script·style는 태그만 벗기면 안쪽 코드가 글자로 남아 화면에 찍힌다 — 통째로 지운다
  doc.querySelectorAll('script, style').forEach((el) => el.remove());
  return (doc.body.textContent || '').trim();
}

function addLine(card, className, text) {
  const el = document.createElement('div');
  el.className = className;
  el.textContent = text;
  card.appendChild(el);
}

/**
 * TourAttractionDTO → 카드 요소.
 *
 * ⚠️ 사진·주소·전화·개요가 없는 콘텐츠가 많고, 없으면 필드 자체가 응답에서 빠진다(전역
 * non_null). `=== null`로 비교하면 항상 실패하므로 값의 유무로만 판단한다.
 * 이미지 주소는 서버가 https로 바꿔 내려주고, 그래도 실패하면(원본 서버 문제 등) 깨진
 * 아이콘이 카드에 남지 않게 요소를 없앤다.
 */
export function buildTourCard(tour) {
  const card = document.createElement('div');
  card.className = 'tour-card';

  const imageUrl = tour.thumbnailUrl || tour.firstImageUrl;
  if (imageUrl && /^https:\/\//i.test(imageUrl)) {
    const img = document.createElement('img');
    img.className = 'tour-card-thumb';
    img.alt = tour.title ? toPlainText(tour.title) : '';
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    img.addEventListener('error', () => img.remove());
    img.src = imageUrl;
    card.appendChild(img);
  }

  if (tour.title) addLine(card, 'tour-card-title', toPlainText(tour.title));

  const address = [tour.addr1, tour.addr2].filter(Boolean).map(toPlainText).join(' ').trim();
  if (address) addLine(card, 'tour-card-meta', `📍 ${address}`);
  if (tour.tel) addLine(card, 'tour-card-meta', `📞 ${toPlainText(tour.tel)}`);

  if (tour.overview) {
    const overview = toPlainText(tour.overview);
    if (overview) addLine(card, 'tour-card-overview', overview);
  }

  // 표시할 필드가 하나도 없는 콘텐츠도 있다 — 빈 상자가 펼쳐지지 않게 한 줄 안내를 둔다
  if (!card.hasChildNodes()) addLine(card, 'tour-card-meta', '자세한 정보가 아직 없어요');

  return card;
}
