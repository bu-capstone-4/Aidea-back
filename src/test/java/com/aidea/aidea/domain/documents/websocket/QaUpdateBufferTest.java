package com.aidea.aidea.domain.documents.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaUpdateBufferTest {

    private static final String DOC_ID = "doc-1";
    private static final String QA_ID = "qa-1";
    private static final String OTHER_QA_ID = "qa-2";

    private QaUpdateBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new QaUpdateBuffer();
    }

    @Test
    void append_accumulatesUpdates_forSameQaId() {
        buffer.append(DOC_ID, QA_ID, new byte[]{1});
        buffer.append(DOC_ID, QA_ID, new byte[]{2});

        assertThat(buffer.currentQaId(DOC_ID)).contains(QA_ID);
        assertThat(buffer.snapshot(DOC_ID, QA_ID)).containsExactly(new byte[]{1}, new byte[]{2});
    }

    @Test
    void append_replacesEntry_whenQaIdChanges() {
        buffer.append(DOC_ID, QA_ID, new byte[]{1});
        buffer.append(DOC_ID, OTHER_QA_ID, new byte[]{2});

        assertThat(buffer.currentQaId(DOC_ID)).contains(OTHER_QA_ID);
        assertThat(buffer.snapshot(DOC_ID, OTHER_QA_ID)).containsExactly(new byte[]{2});
        assertThat(buffer.snapshot(DOC_ID, QA_ID)).isEmpty();
    }

    @Test
    void snapshot_returnsEmpty_whenQaIdDoesNotMatch() {
        buffer.append(DOC_ID, QA_ID, new byte[]{1});

        assertThat(buffer.snapshot(DOC_ID, OTHER_QA_ID)).isEmpty();
        assertThat(buffer.snapshot("unknown-doc", QA_ID)).isEmpty();
    }

    @Test
    void snapshot_returnsCopy() {
        buffer.append(DOC_ID, QA_ID, new byte[]{1});

        buffer.snapshot(DOC_ID, QA_ID).clear();

        assertThat(buffer.snapshot(DOC_ID, QA_ID)).hasSize(1);
    }

    @Test
    void clear_removesEntry() {
        buffer.append(DOC_ID, QA_ID, new byte[]{1});

        buffer.clear(DOC_ID);

        assertThat(buffer.currentQaId(DOC_ID)).isEmpty();
        assertThat(buffer.snapshot(DOC_ID, QA_ID)).isEmpty();
    }

    @Test
    void append_returnsFalse_whenTotalBytesExceedLimit() {
        byte[] almostFull = new byte[(int) QaUpdateBuffer.MAX_TOTAL_BYTES];

        assertThat(buffer.append(DOC_ID, QA_ID, almostFull)).isTrue();
        assertThat(buffer.append(DOC_ID, QA_ID, new byte[]{1})).isFalse();
        assertThat(buffer.snapshot(DOC_ID, QA_ID)).hasSize(1);
    }
}
