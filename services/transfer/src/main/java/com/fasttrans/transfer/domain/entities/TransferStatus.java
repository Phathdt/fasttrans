package com.fasttrans.transfer.domain.entities;

/** Lifecycle of a transfer. Stored as its name() string in the DB (PENDING|COMPLETED|FAILED). */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED
}
