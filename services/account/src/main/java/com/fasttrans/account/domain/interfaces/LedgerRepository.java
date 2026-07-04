package com.fasttrans.account.domain.interfaces;

import com.fasttrans.account.domain.entities.LedgerEntry;

/** Domain contract for appending ledger entries. Implemented in infrastructure. */
public interface LedgerRepository {

    /** Appends a single ledger entry (DEBIT or CREDIT). */
    void save(LedgerEntry entry);
}
