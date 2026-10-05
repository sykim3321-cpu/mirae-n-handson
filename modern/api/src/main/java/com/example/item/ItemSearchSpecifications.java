package com.example.item;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 문항 검색의 WHERE 절. 레거시 search.php 가 {@code v_item_public} 뷰에 붙이던 조건을 옮겼다.
 *
 * <p>비교 · 정렬은 모두 DB 에서 한다. 단원 코드의 대소문자 무시(BR-09)와 제목 정렬 순서는
 * MariaDB 콜레이션({@code utf8mb4_unicode_ci})이 정하므로 자바에서 값을 바꾸거나 거르지 않는다.
 */
final class ItemSearchSpecifications {

    private ItemSearchSpecifications() {
    }

    static Specification<Item> of(ItemSearchCondition condition) {
        List<Specification<Item>> specs = new ArrayList<>();
        specs.add(publicOnly());
        if (!condition.keyword().isEmpty()) {
            specs.add(keyword(condition.keyword()));
        }
        if (!condition.unitCode().isEmpty()) {
            specs.add(unitCode(condition.unitCode()));
        }
        specs.add(level(condition.level()));
        if (!condition.tag().isEmpty()) {
            specs.add(tag(condition.tag()));
        }
        return Specification.allOf(specs);
    }

    /**
     * 공개 문항만(BR-01). 뷰 {@code v_item_public} 의 {@code WHERE i.status = 'A'} 와 같다.
     * 뷰의 {@code JOIN unit} 은 {@code item.unit_id} 가 NOT NULL 외래 키라 걸러 내는 행이 없다.
     */
    static Specification<Item> publicOnly() {
        return (root, query, cb) -> cb.equal(root.get("status"), ItemStatus.ACTIVE);
    }

    /** 제목 또는 지문 부분 일치(BR-04). {@code %} · {@code _} 를 이스케이프하지 않는다(BR-15). */
    static Specification<Item> keyword(String keyword) {
        String like = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(
            cb.like(root.get("title"), like),
            cb.like(root.get("stem"), like));
    }

    /** 단원 코드 정확 일치(BR-09). 입력값을 그대로 비교한다. */
    static Specification<Item> unitCode(String unitCode) {
        return (root, query, cb) -> cb.equal(root.get("unit").get("code"), unitCode);
    }

    /**
     * 난이도(BR-06~BR-08). {@code null} 이면 {@code level < 5}, 아니면 {@code level = 값}.
     * int 범위를 넘는 값은 어떤 문항과도 같을 수 없으므로 거짓 조건으로 둔다.
     */
    static Specification<Item> level(Long level) {
        return (root, query, cb) -> {
            if (level == null) {
                return cb.lessThan(root.get("level"), ItemSearchCondition.UNSPECIFIED_LEVEL_BELOW);
            }
            if (level < Integer.MIN_VALUE || level > Integer.MAX_VALUE) {
                return cb.disjunction();
            }
            return cb.equal(root.get("level"), level.intValue());
        };
    }

    /** 태그 이름이 정확히 같은 태그가 하나라도 붙은 문항(BR-12). */
    static Specification<Item> tag(String tagName) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<Item> item = sub.correlate(root);
            Join<Item, Tag> tag = item.join("tags");
            sub.select(tag.get("id")).where(cb.equal(tag.get("name"), tagName));
            return cb.exists(sub);
        };
    }
}
