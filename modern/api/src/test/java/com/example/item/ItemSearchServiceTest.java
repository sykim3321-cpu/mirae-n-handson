package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 문항 검색 — H2(MariaDB 모드)에 실제 쿼리를 날린다.
 * H2 는 콜레이션이 달라 대소문자 무시 · 한글 정렬 순서는 여기서 보지 않는다(동작 보존 테스트가 MariaDB 에서 본다).
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItemSearchService.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemSearchService itemSearchService;

    private Integer easy;
    private Integer hardest;
    private Integer mid;
    private Integer word;
    private Integer decimal;

    @BeforeEach
    void seed() {
        Unit m51 = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit m53 = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag story = entityManager.persist(ItemFixtures.tag(2, "문장제"));
        Tag hard = entityManager.persist(ItemFixtures.tag(4, "오답률높음"));

        easy = entityManager.persist(ItemFixtures.item(null, m51, "분모가 같은 분수의 덧셈", 1, ItemStatus.ACTIVE, calc)).getId();
        mid = entityManager.persist(ItemFixtures.item(null, m51, "대분수의 덧셈", 3, ItemStatus.ACTIVE, hard, calc)).getId();
        word = entityManager.persist(ItemFixtures.item(null, m51, "분수의 뺄셈 문장제", 4, ItemStatus.ACTIVE, story)).getId();
        hardest = entityManager.persist(ItemFixtures.item(null, m51, "분수 혼합 계산", 5, ItemStatus.ACTIVE, calc)).getId();
        decimal = entityManager.persist(ItemFixtures.item(null, m53, "소수끼리의 곱셈", 3, ItemStatus.ACTIVE, calc)).getId();
        entityManager.persist(ItemFixtures.item(null, m51, "검수 중 문항", 3, ItemStatus.REVIEWING, calc));
        entityManager.persist(ItemFixtures.item(null, m51, "삭제된 문항", 5, ItemStatus.DELETED, calc));
        entityManager.flush();
        entityManager.clear();
    }

    private ItemSearchResponse search(String... keyValues) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        return itemSearchService.search(ItemSearchCondition.from(params));
    }

    @Test
    @DisplayName("조건 없음 — 공개 문항 중 난이도 5 제외, 난이도 내림차순 · id 오름차순")
    void noConditionExcludesLevel5AndNonPublic() {
        ItemSearchResponse response = search();

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.count()).isEqualTo(4);
        assertThat(response.message()).isNull();
        assertThat(response.rows()).extracting(ItemSearchRow::id).containsExactly(word, mid, decimal, easy);
    }

    @Test
    @DisplayName("행에는 id · 제목 · 단원 코드 · 난이도 · 태그(id 순)만 담긴다")
    void rowShape() {
        ItemSearchRow row = search("level", "3", "unit", "M5-1").rows().get(0);

        assertThat(row).isEqualTo(new ItemSearchRow(mid, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음")));
    }

    @Test
    @DisplayName("난이도 5 명시 — 난이도 5 공개 문항만, 삭제 문항 제외")
    void level5Explicit() {
        ItemSearchResponse response = search("level", "5");

        assertThat(response.rows()).extracting(ItemSearchRow::id).containsExactly(hardest);
        assertThat(response.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("난이도 6 · abc · 아주 큰 수 — 오류 없이 0건, 안내 문구")
    void outOfRangeLevelIsEmpty() {
        for (String level : new String[] {"6", "abc", "99999999999999999999"}) {
            ItemSearchResponse response = search("level", level);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.count()).isZero();
            assertThat(response.rows()).isEmpty();
            assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
        }
    }

    @Test
    @DisplayName("난이도 3abc — 난이도 3 으로 조회")
    void levelWithTrailingGarbage() {
        assertThat(search("level", "3abc").rows()).extracting(ItemSearchRow::id).containsExactly(mid, decimal);
    }

    @Test
    @DisplayName("없는 단원 코드 — 404 가 아니라 0건")
    void unknownUnitIsEmpty() {
        ItemSearchResponse response = search("unit", "M9-99");

        assertThat(response.count()).isZero();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("난이도 + 태그 — AND 결합, 태그 정확 일치")
    void levelAndTag() {
        assertThat(search("level", "3", "tag", "계산").rows()).extracting(ItemSearchRow::id)
            .containsExactly(mid, decimal);
        assertThat(search("tag", "계").count()).isZero();
    }

    @Test
    @DisplayName("키워드 — 제목 · 지문 부분 일치, % 와 _ 는 와일드카드 그대로")
    void keywordWildcards() {
        assertThat(search("q", "덧셈").rows()).extracting(ItemSearchRow::id).containsExactly(mid, easy);
        assertThat(search("q", "문제 본문").count()).isEqualTo(4);
        assertThat(search("q", "분_의").rows()).extracting(ItemSearchRow::id).containsExactly(word, mid, easy);
        assertThat(search("q", "%".repeat(100) + "☆").count()).isEqualTo(4);
    }

    @Test
    @DisplayName("단원 정렬 — 단원 코드 다음 난이도 내림차순, id 오름차순")
    void sortByUnit() {
        assertThat(search("sort", "unit", "dir", "desc").rows()).extracting(ItemSearchRow::id)
            .containsExactly(decimal, word, mid, easy);
    }

    @Test
    @DisplayName("id 내림차순 정렬")
    void sortByIdDesc() {
        assertThat(search("sort", "id", "dir", "desc").rows()).extracting(ItemSearchRow::id)
            .containsExactly(decimal, word, mid, easy);
    }

    @Test
    @DisplayName("마지막 페이지를 넘는 번호 — 건수는 그대로, 행은 비고 안내 문구 없음")
    void pageBeyondLast() {
        ItemSearchResponse response = search("page", "1000");

        assertThat(response.count()).isEqualTo(4);
        assertThat(response.rows()).isEmpty();
        assertThat(response.message()).isNull();
    }
}
