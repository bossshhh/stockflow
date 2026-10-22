package com.stockflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    @Test
    void searchPatternIsLowercasedTrimmedAndWrapped() {
        assertThat(CatalogService.containsPattern("  Apple ")).isEqualTo("%apple%");
    }

    @Test
    void likeWildcardsAndTheEscapeCharacterAreEscaped() {
        assertThat(CatalogService.containsPattern("50%_off\\x")).isEqualTo("%50\\%\\_off\\\\x%");
    }
}
