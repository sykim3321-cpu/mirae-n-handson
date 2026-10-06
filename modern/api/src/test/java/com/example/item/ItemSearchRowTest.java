package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** 엔티티 → 검색 행 변환 — 레거시가 뷰의 tag_names(쉼표 연결)를 그대로 찍던 결과와 같은지. */
@ActiveProfiles("test")
class ItemSearchRowTest {

    private static final Unit M51 = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);

    @Test
    @DisplayName("id · 제목 · 단원 코드 · 난이도 · 태그 이름(붙인 순서)을 담는다")
    void mapsFields() {
        Item item = ItemFixtures.item(24, M51, "분수 혼합 계산", 5, ItemStatus.ACTIVE,
            ItemFixtures.tag(1, "계산"), ItemFixtures.tag(4, "오답률높음"));

        assertThat(ItemSearchRow.from(item))
            .isEqualTo(new ItemSearchRow(24, "분수 혼합 계산", "M5-1", 5, List.of("계산", "오답률높음")));
    }

    @Test
    @DisplayName("태그가 없으면 빈 목록(빈 문자열 하나가 아니다)")
    void noTagsIsEmptyList() {
        Item item = ItemFixtures.item(7, M51, "태그 없는 문항", 2, ItemStatus.ACTIVE);

        assertThat(ItemSearchRow.from(item).tags()).isEmpty();
    }

    @Test
    @DisplayName("태그 이름에 쉼표가 있으면 둘로 나뉜다 — 레거시 그대로")
    void tagNameWithCommaIsSplit() {
        Item item = ItemFixtures.item(8, M51, "쉼표 태그 문항", 2, ItemStatus.ACTIVE,
            ItemFixtures.tag(1, "계산"), ItemFixtures.tag(9, "분수,소수"));

        assertThat(ItemSearchRow.from(item).tags()).containsExactly("계산", "분수", "소수");
    }

    @Test
    @DisplayName("쉼표만으로 생긴 빈 조각은 버린다")
    void emptyPiecesAreDropped() {
        Item item = ItemFixtures.item(9, M51, "빈 조각 문항", 2, ItemStatus.ACTIVE,
            ItemFixtures.tag(1, ",계산,"), ItemFixtures.tag(2, ""));

        assertThat(ItemSearchRow.from(item).tags()).containsExactly("계산");
    }
}
