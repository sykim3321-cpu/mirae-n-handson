package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** 검색 응답 — 안내 문구는 페이지의 행 수가 아니라 전체 건수로 정한다. */
@ActiveProfiles("test")
class ItemSearchResponseTest {

    @Test
    @DisplayName("전체 0건 — 상태 200, 안내 문구 \"검색 결과가 없습니다\"")
    void zeroCountHasMessage() {
        ItemSearchResponse response = ItemSearchResponse.of(List.of(), 0);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isZero();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("행은 비어도 전체 건수가 있으면 안내 문구 없음(null)")
    void emptyPageWithCountHasNoMessage() {
        ItemSearchResponse response = ItemSearchResponse.of(List.of(), 31);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.count()).isEqualTo(31);
        assertThat(response.message()).isNull();
    }
}
