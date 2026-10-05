package com.example.item;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스 — 레거시 search.php 의 이관. 없는 단원 · 범위 밖 난이도도 오류가 아니라 0건이다.
 * 엔티티는 트랜잭션 안에서 DTO 로 바꿔 돌려준다(OSIV 꺼져 있음).
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;

    public ItemSearchService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public ItemSearchResponse search(ItemSearchCondition condition) {
        Page<Item> page = itemRepository.findAll(ItemSearchSpecifications.of(condition), condition.toPageable());
        List<ItemSearchRow> rows = page.getContent().stream()
            .map(ItemSearchRow::from)
            .toList();
        log.debug("item search {} → total {}, page rows {}", condition, page.getTotalElements(), rows.size());
        return ItemSearchResponse.of(rows, page.getTotalElements());
    }
}
