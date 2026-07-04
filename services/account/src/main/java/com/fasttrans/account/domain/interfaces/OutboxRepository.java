package com.fasttrans.account.domain.interfaces;

import com.fasttrans.account.domain.entities.TransferResult;

/** Domain contract for enqueuing transfer results to the transactional outbox. */
public interface OutboxRepository {

    /** Enqueues a transfer result for publication to transfer.result (serialized in infrastructure). */
    void enqueue(TransferResult result);
}
