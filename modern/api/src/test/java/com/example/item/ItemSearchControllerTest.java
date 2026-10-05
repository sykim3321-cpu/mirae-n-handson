package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 응답 JSON 이 동작 보존 테스트의 정규화 모양과 같은지. */
@WebMvcTest(ItemSearchController.class)
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @Test
    @DisplayName("GET /api/items/search → 200, {status, rows, count, message}, 행에는 다섯 필드만")
    void searchReturnsNormalizedShape() throws Exception {
        when(itemSearchService.search(any())).thenReturn(ItemSearchResponse.of(
            List.of(new ItemSearchRow(24, "분수 덧셈과 뺄셈 혼합 계산", "M5-1", 5, List.of("계산", "오답률높음"))), 6));

        mockMvc.perform(get("/api/items/search").param("level", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.count").value(6))
            .andExpect(jsonPath("$.message").value(nullValue()))
            .andExpect(jsonPath("$.rows[0].id").value(24))
            .andExpect(jsonPath("$.rows[0].title").value("분수 덧셈과 뺄셈 혼합 계산"))
            .andExpect(jsonPath("$.rows[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.rows[0].level").value(5))
            .andExpect(jsonPath("$.rows[0].tags[1]").value("오답률높음"))
            .andExpect(jsonPath("$.rows[0].length()").value(5))
            .andExpect(jsonPath("$.rows[0].stem").doesNotExist())
            .andExpect(jsonPath("$.rows[0].status").doesNotExist());
    }

    @Test
    @DisplayName("0건 — 200, 안내 문구 \"검색 결과가 없습니다\"")
    void emptyResultHasMessage() throws Exception {
        when(itemSearchService.search(any())).thenReturn(ItemSearchResponse.of(List.of(), 0));

        mockMvc.perform(get("/api/items/search").param("unit", "m9-99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.rows").isEmpty())
            .andExpect(jsonPath("$.message").value("검색 결과가 없습니다"));
    }

    @Test
    @DisplayName("숫자가 아닌 난이도 · 음수 페이지도 400 이 아니라 레거시 규칙으로 해석한다")
    void oddValuesAreNotRejected() throws Exception {
        when(itemSearchService.search(any())).thenReturn(ItemSearchResponse.of(List.of(), 0));

        mockMvc.perform(get("/api/items/search").param("level", "3abc").param("page", "-1"))
            .andExpect(status().isOk());

        ArgumentCaptor<ItemSearchCondition> captor = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemSearchService).search(captor.capture());
        assertThat(captor.getValue().level()).isEqualTo(3L);
        assertThat(captor.getValue().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("레거시 경로 /search.php 는 404 — 동작 보존 테스트가 PATH_ALIASES 의 새 경로로 다시 요청한다")
    void legacyPathIs404() throws Exception {
        mockMvc.perform(get("/search.php").param("q", "분수"))
            .andExpect(status().isNotFound());
    }
}
