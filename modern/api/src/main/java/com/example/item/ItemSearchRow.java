package com.example.item;

import java.util.Arrays;
import java.util.List;

/**
 * 문항 검색 결과 한 행. 레거시 결과 표의 열(id, title, unit, level, tags)만 담는다.
 * {@code unit} 은 단원 코드, {@code tags} 는 태그 이름(id 순).
 */
public record ItemSearchRow(Integer id, String title, String unit, Integer level, List<String> tags) {

    /**
     * 레거시는 뷰가 태그 이름을 쉼표로 이어 붙인 {@code tag_names} 를 표에 그대로 찍었다.
     * 같은 결과가 되도록 이어 붙였다가 쉼표로 다시 나눈다(이름에 쉼표가 있으면 둘로 나뉜다 — 레거시 그대로).
     */
    static ItemSearchRow from(Item item) {
        String tagNames = String.join(",", item.getTags().stream().map(Tag::getName).toList());
        List<String> tags = Arrays.stream(tagNames.split(","))
            .filter(name -> !name.isEmpty())
            .toList();
        return new ItemSearchRow(item.getId(), item.getTitle(), item.getUnit().getCode(), item.getLevel(), tags);
    }
}
