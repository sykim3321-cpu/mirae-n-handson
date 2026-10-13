// 문항 조회 API 클라이언트 (실습용 더미)
//
// 실제 사내 API 대신 메모리 안의 배열을 돌려준다. 외부 서비스 없이 동작한다.
// 문항 id · 단원 코드는 itembank DB(db/mariadb/init/02-seed.sql)와 같게 맞췄다.
// 단, 표현은 일부러 다르다:
//   - 난이도: DB는 level 정수(1~5), 이 API는 문자열 '하' · '중' · '상'
//   - 단원: DB는 unit_id(정수) + unit 테이블, 이 API는 단원 코드 문자열('M5-1' 등)
//
// 이 파일은 "감쌀 대상"이다. MCP 도구를 추가할 때 이 파일은 고치지 않는다.

export type Difficulty = '하' | '중' | '상';

export interface Item {
  id: number;
  unit: string; // 단원 코드 (M5-1 … M6-2)
  difficulty: string; // '하' | '중' | '상'
  tags: string[];
  stem: string;
}

export interface SearchParams {
  keyword: string;
  unit?: string; // 단원 코드 또는 단원 이름
  difficulty?: string; // '하' | '중' | '상'
  limit?: number; // 기본 5
}

// 단원 코드 → 단원 이름 (itembank.unit 과 같다)
export const UNITS: Record<string, string> = {
  'M5-1': '분수의 덧셈과 뺄셈',
  'M5-2': '분수의 곱셈',
  'M5-3': '소수의 곱셈',
  'M6-1': '분수의 나눗셈',
  'M6-2': '비와 비율'
};

const items: Item[] = [
  { id: 1, unit: 'M5-1', difficulty: '하', tags: ['계산', '개념'], stem: '분모가 같은 분수 3/7 + 2/7 을 계산하시오.' },
  { id: 2, unit: 'M5-1', difficulty: '하', tags: ['계산'], stem: '분모가 같은 분수 5/9 - 2/9 를 계산하시오.' },
  { id: 3, unit: 'M5-3', difficulty: '하', tags: ['계산'], stem: '0.3 × 4 를 계산하시오.' },
  { id: 4, unit: 'M6-2', difficulty: '하', tags: ['개념'], stem: '사과 3개와 배 5개의 개수를 비로 나타내시오.' },
  { id: 6, unit: 'M5-1', difficulty: '하', tags: ['계산', '오답률높음'], stem: '분모가 다른 분수 1/2 + 1/3 을 통분하여 계산하시오.' },
  { id: 8, unit: 'M6-2', difficulty: '하', tags: ['개념'], stem: '비 3:4 의 비율을 분수로 나타내시오.' },
  { id: 12, unit: 'M5-1', difficulty: '중', tags: ['계산', '오답률높음'], stem: '대분수 1과 1/4 + 2와 2/3 을 계산하시오.' },
  { id: 13, unit: 'M5-2', difficulty: '중', tags: ['계산'], stem: '대분수의 곱셈 1과 1/2 × 2와 2/5 를 계산하시오.' },
  { id: 14, unit: 'M5-3', difficulty: '중', tags: ['계산'], stem: '2.4 × 1.5 를 계산하시오.' },
  { id: 15, unit: 'M6-1', difficulty: '중', tags: ['계산', '개념'], stem: '자연수를 분수로 나누는 식 6 ÷ 3/4 를 계산하시오.' },
  { id: 16, unit: 'M6-2', difficulty: '중', tags: ['문장제'], stem: '전체 50명 중 12명이 안경을 씁니다. 안경을 쓴 학생의 비율을 백분율로 나타내시오.' },
  { id: 18, unit: 'M5-1', difficulty: '상', tags: ['문장제', '오답률높음'], stem: '리본 2와 1/6 m 중 5/8 m 를 사용했습니다. 남은 리본의 길이를 분수로 구하시오.' },
  { id: 20, unit: 'M5-3', difficulty: '상', tags: ['문장제', '도형'], stem: '한 변의 길이가 1.8 m 인 정사각형 화단의 넓이를 구하시오.' },
  { id: 21, unit: 'M6-1', difficulty: '상', tags: ['문장제', '오답률높음'], stem: '주스 3/4 L 를 한 사람에게 1/8 L 씩 나누어 주면 몇 명에게 줄 수 있는지 구하시오.' },
  { id: 25, unit: 'M5-2', difficulty: '상', tags: ['문장제', '서술형'], stem: '어떤 수에 분수 3/4 을 곱했더니 9/10 이 되었습니다. 어떤 수를 구하고 풀이 과정을 쓰시오.' },
  { id: 27, unit: 'M6-1', difficulty: '상', tags: ['개념', '서술형'], stem: '분수의 나눗셈 3/5 ÷ 2/7 을 곱셈으로 바꾸어 계산할 수 있는 이유를 설명하시오.' }
];

function matchesUnit(item: Item, unit: string): boolean {
  const q = unit.trim();
  if (q === '') return true;
  return item.unit === q || (UNITS[item.unit] ?? '').includes(q);
}

/**
 * 문항 검색.
 * - keyword: 문항 본문(stem) 또는 태그에 포함되면 일치
 * - unit: 단원 코드('M5-1') 또는 단원 이름 일부('분수의 곱셈')
 * - difficulty: '하' | '중' | '상'
 * - limit: 최대 건수(기본 5)
 */
export async function searchItems({ keyword, unit, difficulty, limit = 5 }: SearchParams): Promise<Item[]> {
  const kw = keyword.trim();
  return items
    .filter((it) => kw === '' || it.stem.includes(kw) || it.tags.some((t) => t.includes(kw)))
    .filter((it) => (unit ? matchesUnit(it, unit) : true))
    .filter((it) => (difficulty ? it.difficulty === difficulty.trim() : true))
    .slice(0, Math.max(0, limit))
    .map((it) => ({ ...it, tags: [...it.tags] }));
}

/**
 * 문항 태그 교체 (심화 2용). 메모리 안의 배열만 바꾼다. 서버를 다시 켜면 원래대로 돌아온다.
 * 없는 id 면 null 을 돌려준다.
 */
export async function updateItemTags(id: number, tags: string[]): Promise<Item | null> {
  const item = items.find((it) => it.id === id);
  if (!item) return null;
  item.tags = [...new Set(tags.map((t) => t.trim()).filter((t) => t !== ''))];
  return { ...item, tags: [...item.tags] };
}
