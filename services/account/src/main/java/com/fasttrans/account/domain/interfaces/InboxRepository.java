package com.fasttrans.account.domain.interfaces;

import java.util.UUID;

/** Domain contract for inbox dedup — ensures idempotent processing of transfer.requested. */
public interface InboxRepository {

    /** True when the message was already processed. */
    boolean isProcessed(UUID messageId);

    /** Marks the message as processed (same transaction as the business write). */
    void markProcessed(UUID messageId, UUID transferId);
}
