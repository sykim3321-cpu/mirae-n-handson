# vendor-prs — 외주사 PR 세 건 (2회차 Day 2-1 실습 3 · 4)

외주 협력사가 보냈다고 **가정한 더미 변경분**입니다. 실제 외주사 · 실제 비밀값이 아닙니다.
세 건 모두 `git format-patch` 형식이라 머리말 첫머리(`Subject:`)에 **발주 측이 요청한 범위 한 문장**이 적혀 있고, 그 아래에 커밋 메시지와 diff가 이어집니다.

| 파일 | 요청 범위 | 쓰는 곳 |
|---|---|---|
| `pr-1-missing-tests.patch` | 단원별 문항 수 통계 API 추가 | Day 2-1 실습 3(전원) · 실습 4 |
| `pr-2-hardcoded-secret.patch` | 외부 채점 서버 연동 클라이언트 추가 | Day 2-1 실습 3(페어 한 명) · Day 3-1 실습 2 |
| `pr-3-out-of-scope.patch` | 문항 검색에 난이도 필터 추가 | Day 2-1 실습 3(페어 한 명) · 실습 4 |

각 건에 무엇이 숨어 있는지는 적지 않습니다. 검증루프로 찾아 보세요.

## 먼저 읽기

```bash
head -n 20 vendor-prs/pr-1-missing-tests.patch   # 요청 문장
git apply --stat  vendor-prs/pr-1-missing-tests.patch   # 바뀌는 파일 목록
git apply --check vendor-prs/pr-1-missing-tests.patch   # 출력이 없으면 적용 가능
```

## 리뷰 브랜치에 적용하기

작업 브랜치(`day1`)에서 브랜치를 새로 따서 적용합니다. `main` 이나 `day1` 에 직접 적용하지 않습니다.

```bash
git switch -c review/pr-1
git apply --index vendor-prs/pr-1-missing-tests.patch
git commit -m "review: vendor pr-1 적용"
```

`--check` 에서 `patch failed` · `does not apply` 가 나오면 1회차 작업이 같은 파일을 건드린 경우입니다. 원본에서 브랜치를 따서 적용하세요.

```bash
git switch -c review/pr-1 upstream/main
git apply --index vendor-prs/pr-1-missing-tests.patch
```

리뷰가 끝나면 `git switch day1` 로 돌아옵니다. 세 건은 서로 독립이라 한 브랜치에 겹쳐 적용하지 않습니다.
