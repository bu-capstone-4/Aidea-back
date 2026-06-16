package com.aidea.aidea.domain.documents.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTest {

    @Test
    void free_displayName_returns자유편집문서() {
        assertThat(DocumentType.FREE.displayName()).isEqualTo("자유 편집 문서");
    }

    @Test
    void free_requiredElements_returnsEmptyString() {
        assertThat(DocumentType.FREE.requiredElements()).isEmpty();
    }

    @Test
    void free_supportsDraftGeneration_returnsFalse() {
        assertThat(DocumentType.FREE.supportsDraftGeneration()).isFalse();
    }

    @Test
    void allNonFreeTypes_supportsDraftGeneration_returnTrue() {
        List<DocumentType> nonFreeTypes = Arrays.stream(DocumentType.values())
                .filter(t -> t != DocumentType.FREE)
                .toList();

        assertThat(nonFreeTypes).isNotEmpty();
        for (DocumentType type : nonFreeTypes) {
            assertThat(type.supportsDraftGeneration())
                    .as("%s.supportsDraftGeneration() should be true", type)
                    .isTrue();
        }
    }

    @Test
    void allTypes_displayName_returnsNonBlank() {
        for (DocumentType type : DocumentType.values()) {
            assertThat(type.displayName())
                    .as("%s.displayName() should not be blank", type)
                    .isNotBlank();
        }
    }
}
