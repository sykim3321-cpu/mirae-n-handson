// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// 규칙 ID(BR-xx)는 docs/item-bank/BUSINESS-RULES.md 를 따른다.
//
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 레거시 기본 주소).
//   이관 후에는 PATH_ALIASES 로 /search.php → /api/items/search 로 이어진다.
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 기대값을 손으로 적지 않는다. 지금 레거시의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 건너뛰기를 두지 않는다.
// - 경고 문구(<ul id="warnings">)는 정규화 결과에 들어가지 않는다. 경고만 내는 규칙은
//   행 · 건수에 드러나는 효과로만 검증된다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

const search = (params) => fetchNormalized(MODULE, PATH, params);

describe('item-bank · 문항 검색(search.php)', () => {
  // ---------------------------------------------------------------- 정상
  describe('정상 입력', () => {
    it('단원 하나 — 비공개 제외 · 난이도 5 제외 · 기본 정렬 (BR-09, BR-01, BR-06, BR-16)', async () => {
      expect(await search({ unit: 'M5-2' })).toMatchSnapshot();
    });

    it('난이도 + 태그 — 조건 AND 결합 · 태그 정확 일치 (BR-03, BR-07, BR-12)', async () => {
      expect(await search({ level: '3', tag: '계산' })).toMatchSnapshot();
    });

    it('키워드 + 제목 내림차순 — 보조 정렬 id 는 오름차순 유지 (BR-04, BR-18, BR-20)', async () => {
      expect(await search({ q: '분수', sort: 'title', dir: 'desc' })).toMatchSnapshot();
    });
  });

  // ---------------------------------------------------------------- 경계값
  describe('경계값', () => {
    it('난이도 5 명시 — 난이도 5 가 조회되는 유일한 경로 · 비공개 제외 (BR-07, BR-06, BR-01)', async () => {
      expect(await search({ level: '5' })).toMatchSnapshot();
    });

    it('난이도 6 — 1~5 범위 바로 밖, 거부하지 않고 정수 비교 (BR-08)', async () => {
      expect(await search({ level: '6' })).toMatchSnapshot();
    });

    // 키워드 길이 경계는 결과가 0건이면 잘림 여부가 드러나지 않는다.
    // 키워드의 % 가 LIKE 와일드카드로 쓰이는 점(BR-15 비고)을 이용해, 잘림 여부가 행으로 드러나게 만든다.
    it('키워드 정확히 100자 — 잘리지 않음 (BR-05, BR-04)', async () => {
      expect(await search({ q: '%'.repeat(99) + '분' })).toMatchSnapshot();
    });

    it('키워드 101자 — 앞 100자로 잘림, 남은 % 만으로 조회 (BR-05, BR-04)', async () => {
      expect(await search({ q: '%'.repeat(100) + '☆' })).toMatchSnapshot();
    });

    it('키워드 한 글자 — 경고만 내고 그대로 조회 (BR-15, BR-04)', async () => {
      expect(await search({ q: '수' })).toMatchSnapshot();
    });

    it('페이지 1000 — 999 로 고정, 건수는 있고 행은 빈 페이지 (BR-21)', async () => {
      expect(await search({ page: '1000' })).toMatchSnapshot();
    });
  });

  // ---------------------------------------------------------------- 빈 값 · 누락
  describe('빈 값 · 누락', () => {
    it('파라미터 전부 누락 — 난이도 5 제외 · 기본 정렬 · 1페이지 (BR-06, BR-16, BR-21, BR-01)', async () => {
      expect(await search({})).toMatchSnapshot();
    });

    it('키는 있고 값이 빈 문자열 · 공백만 — 누락과 같은 처리 (BR-04, BR-06, BR-16, BR-19, BR-21)', async () => {
      expect(
        await search({ q: '  ', unit: '', level: '', tag: '', sort: '', dir: '', page: '' }),
      ).toMatchSnapshot();
    });
  });

  // ---------------------------------------------------------------- 이상한 값
  describe('이상한 값', () => {
    it('음수 페이지 -1 — 1페이지로 처리 (BR-21)', async () => {
      expect(await search({ page: '-1' })).toMatchSnapshot();
    });

    it('숫자로 시작하는 난이도 3abc — 정수 3 으로 변환해 조회 (BR-08)', async () => {
      expect(await search({ level: '3abc' })).toMatchSnapshot();
    });

    it('없는 단원 코드 · 소문자 m9-99 — 경고만 내고 그대로 조회 (BR-09, BR-10, BR-11)', async () => {
      expect(await search({ unit: 'm9-99' })).toMatchSnapshot();
    });

    it('아주 긴 태그 62자 — 50자로 잘린 뒤 없는 태그로 조회 (BR-13, BR-14, BR-12)', async () => {
      expect(await search({ tag: '계산' + 'x'.repeat(60) })).toMatchSnapshot();
    });

    it('알 수 없는 정렬 DROP · 방향 up — 기본 정렬로 대체 (BR-17, BR-19, BR-16, BR-01)', async () => {
      expect(await search({ sort: 'DROP', dir: 'up', unit: 'M5-1' })).toMatchSnapshot();
    });
  });
});
