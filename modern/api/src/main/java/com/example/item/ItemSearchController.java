package com.example.item;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 검색 API — 레거시 {@code search.php} 의 이관. 파라미터 이름은 레거시와 같다
 * ({@code q, unit, level, tag, sort, dir, page}).
 */
@RestController
@RequestMapping("/api/items")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /**
     * {@code GET /api/items/search} — 파라미터는 모두 문자열로 받는다.
     * {@code level=3abc} · {@code page=-1} 같은 값도 400 이 아니라 레거시 규칙대로 해석한다.
     */
    @GetMapping("/search")
    public ItemSearchResponse search(@RequestParam MultiValueMap<String, String> params) {
        return itemSearchService.search(ItemSearchCondition.from(params));
    }
}
