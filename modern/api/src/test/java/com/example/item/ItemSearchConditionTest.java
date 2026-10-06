package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** 검색 파라미터 해석 — 레거시 search.php 의 PHP 동작(trim · (int) · mb_substr)을 그대로 따르는지. */
@ActiveProfiles("test")
class ItemSearchConditionTest {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));

    private static ItemSearchCondition parse(String... keyValues) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        return ItemSearchCondition.from(params);
    }

    @Test
    @DisplayName("파라미터 전부 누락 — 조건 없음, 난이도는 null(= 5 미만), 기본 정렬, 1페이지")
    void emptyParams() {
        ItemSearchCondition c = parse();

        assertThat(c.keyword()).isEmpty();
        assertThat(c.unitCode()).isEmpty();
        assertThat(c.level()).isNull();
        assertThat(c.tag()).isEmpty();
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.toSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    @DisplayName("빈 문자열 · 공백만 — 누락과 같은 처리")
    void blankParamsSameAsMissing() {
        ItemSearchCondition c = parse("q", "  ", "unit", "", "level", "", "tag", "", "sort", "", "dir", "", "page", "");

        assertThat(c).isEqualTo(parse());
    }

    @Test
    @DisplayName("키워드 · 단원 · 태그는 앞뒤 공백만 지우고 대소문자는 그대로 둔다")
    void trimsWithoutChangingCase() {
        ItemSearchCondition c = parse("q", " 분수 ", "unit", "\tm5-1\n", "tag", " 계산 ");

        assertThat(c.keyword()).isEqualTo("분수");
        assertThat(c.unitCode()).isEqualTo("m5-1");
        assertThat(c.tag()).isEqualTo("계산");
    }

    @Test
    @DisplayName("PHP trim 은 전각 공백 · NBSP 를 지우지 않는다")
    void phpTrimKeepsUnicodeSpaces() {
        assertThat(ItemSearchCondition.phpTrim("　분수 ")).isEqualTo("　분수 ");
        assertThat(ItemSearchCondition.phpTrim("\0\u000B 분수 \r\n")).isEqualTo("분수");
    }

    @Test
    @DisplayName("키워드 100자는 그대로, 101자는 앞 100자로 자른다(글자 수 기준)")
    void keywordCutAt100CodePoints() {
        String hundred = "%".repeat(99) + "분";
        assertThat(parse("q", hundred).keyword()).isEqualTo(hundred);
        assertThat(parse("q", "%".repeat(100) + "☆").keyword()).isEqualTo("%".repeat(100));
        // 보충 평면 문자도 한 글자로 센다(mb_strlen)
        assertThat(parse("q", "😀".repeat(101)).keyword()).isEqualTo("😀".repeat(100));
    }

    @Test
    @DisplayName("태그 62자는 앞 50자로 자른다")
    void tagCutAt50() {
        assertThat(parse("tag", "계산" + "x".repeat(60)).tag()).isEqualTo("계산" + "x".repeat(48));
    }

    @Test
    @DisplayName("난이도 — 1~5 는 그 값, 3abc · 3.5 · 03 은 3, abc 는 0, 6 은 6")
    void levelUsesPhpIntval() {
        assertThat(parse("level", "3").level()).isEqualTo(3L);
        assertThat(parse("level", "3abc").level()).isEqualTo(3L);
        assertThat(parse("level", "3.5").level()).isEqualTo(3L);
        assertThat(parse("level", "03").level()).isEqualTo(3L);
        assertThat(parse("level", "abc").level()).isZero();
        assertThat(parse("level", "6").level()).isEqualTo(6L);
        assertThat(parse("level", " 5 ").level()).isEqualTo(5L);
    }

    @Test
    @DisplayName("PHP (int) — 지수 · 부호 · 범위 초과")
    void phpIntvalEdgeCases() {
        assertThat(ItemSearchCondition.phpIntval("1e1")).isEqualTo(10L);
        assertThat(ItemSearchCondition.phpIntval("+4")).isEqualTo(4L);
        assertThat(ItemSearchCondition.phpIntval("-2x")).isEqualTo(-2L);
        assertThat(ItemSearchCondition.phpIntval(".9")).isZero();
        assertThat(ItemSearchCondition.phpIntval("99999999999999999999")).isEqualTo(Long.MAX_VALUE);
        assertThat(ItemSearchCondition.phpIntval("-99999999999999999999")).isEqualTo(Long.MIN_VALUE);
        assertThat(ItemSearchCondition.phpIntval("1e999")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("페이지 — 음수 · 0 · 숫자 아님은 1, 999 초과는 999, 공백이 붙으면 1")
    void pageClamp() {
        assertThat(parse("page", "-1").page()).isEqualTo(1);
        assertThat(parse("page", "0").page()).isEqualTo(1);
        assertThat(parse("page", "abc").page()).isEqualTo(1);
        assertThat(parse("page", "3").page()).isEqualTo(3);
        assertThat(parse("page", "1000").page()).isEqualTo(999);
        assertThat(parse("page", "99999999999999999999").page()).isEqualTo(999);
        assertThat(parse("page", " 2").page()).isEqualTo(1);
        assertThat(parse("page", "2\n").page()).isEqualTo(2);
    }

    @Test
    @DisplayName("페이지 → 한 페이지 20건의 offset")
    void pageable() {
        Pageable pageable = parse("page", "999").toPageable();

        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getOffset()).isEqualTo(998L * 20);
    }

    @Test
    @DisplayName("알 수 없는 정렬 DROP · 방향 up — 기본 정렬")
    void unknownSortFallsBackToDefault() {
        ItemSearchCondition c = parse("sort", "DROP", "dir", "up");

        assertThat(c.sort()).isEmpty();
        assertThat(c.dir()).isEmpty();
        assertThat(c.toSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    @DisplayName("정렬 — 대소문자 무시, 기준별 기본 방향, 보조 정렬 id 는 항상 오름차순")
    void sortOrders() {
        assertThat(parse("sort", "ID", "dir", "DESC").toSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
        assertThat(parse("sort", "title", "dir", "desc").toSort())
            .isEqualTo(Sort.by(Sort.Order.desc("title"), Sort.Order.asc("id")));
        assertThat(parse("sort", "title").toSort())
            .isEqualTo(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));
        assertThat(parse("sort", "unit", "dir", "desc").toSort())
            .isEqualTo(Sort.by(Sort.Order.desc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(parse("sort", "level").toSort())
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(parse("sort", "level", "dir", "asc").toSort())
            .isEqualTo(Sort.by(Sort.Order.asc("level"), Sort.Order.asc("id")));
        assertThat(parse("sort", "created").toSort())
            .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("정렬 — 단원 기본 방향은 오름차순, 난이도 · 등록일은 desc 를 줘도 내림차순, 등록일 asc 는 오름차순")
    void sortDefaultDirections() {
        assertThat(parse("sort", "unit").toSort())
            .isEqualTo(Sort.by(Sort.Order.asc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(parse("sort", "id").toSort()).isEqualTo(Sort.by(Sort.Order.asc("id")));
        assertThat(parse("sort", "level", "dir", "desc").toSort())
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(parse("sort", "created", "dir", "asc").toSort())
            .isEqualTo(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        assertThat(parse("sort", "created", "dir", "desc").toSort())
            .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("정렬 없이 방향만 주면 기본 정렬 그대로")
    void dirWithoutSortKeepsDefault() {
        assertThat(parse("dir", "asc").toSort()).isEqualTo(DEFAULT_SORT);
        assertThat(parse("dir", "desc").toSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    @DisplayName("정렬 · 방향은 앞뒤 공백을 지운 뒤 판정한다")
    void sortAndDirAreTrimmed() {
        ItemSearchCondition c = parse("sort", " Title ", "dir", "\tDesc\n");

        assertThat(c.sort()).isEqualTo("title");
        assertThat(c.dir()).isEqualTo("desc");
    }

    @Test
    @DisplayName("난이도 0 · 음수는 null 이 아니라 그 값(= 0건 조건)")
    void zeroAndNegativeLevelAreKept() {
        assertThat(parse("level", "0").level()).isZero();
        assertThat(parse("level", "-1").level()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("페이지 — 999 는 그대로, 앞자리 0 은 무시(007 → 7)")
    void pageBoundaryAndLeadingZero() {
        assertThat(parse("page", "999").page()).isEqualTo(999);
        assertThat(parse("page", "007").page()).isEqualTo(7);
        assertThat(parse("page", "1.5").page()).isEqualTo(1);
    }

    @Test
    @DisplayName("일반 · 배열 형태가 함께 오면 일반 형태 값을 쓴다")
    void plainWinsOverArray() {
        assertThat(parse("q[]", "a", "q", "b").keyword()).isEqualTo("b");
    }

    @Test
    @DisplayName("같은 이름이 여러 번 오면 마지막 값, 배열(q[])이면 첫 값")
    void repeatedAndArrayParams() {
        assertThat(parse("q", "a", "q", "b").keyword()).isEqualTo("b");
        assertThat(parse("q[]", "a", "q[]", "b").keyword()).isEqualTo("a");
    }
}
