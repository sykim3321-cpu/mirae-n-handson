package com.example.item;

import java.util.List;

/**
 * {@code GET /api/items/search} 응답. 필드 이름은 동작 보존 테스트의 정규화 결과
 * {@code {status, rows, count, message}} 와 같다.
 *
 * @param status HTTP 상태 코드(항상 200)
 * @param rows 현재 페이지의 행
 * @param count 전체 건수(페이지와 무관)
 * @param message 전체 건수가 0 이면 "검색 결과가 없습니다", 아니면 null
 */
public record ItemSearchResponse(int status, List<ItemSearchRow> rows, long count, String message) {

    static final int OK = 200;
    static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    static ItemSearchResponse of(List<ItemSearchRow> rows, long count) {
        return new ItemSearchResponse(OK, rows, count, count == 0 ? NO_RESULT_MESSAGE : null);
    }
}
