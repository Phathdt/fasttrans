package com.fasttrans.transfer.domain.interfaces;

import java.util.UUID;

/** Domain contract for inbox dedup — idempotent consumption of transfer.result. */
public interface InboxRepository {

    /** True when the message was already processed. */
    boolean isProcessed(UUID messageId);

    /** Marks the message as processed (same transaction as the transfer update). */
    void markProcessed(UUID messageId);
}
