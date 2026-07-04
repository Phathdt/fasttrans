package com.fasttrans.transfer.domain.interfaces;

import com.fasttrans.transfer.domain.entities.AccountView;

import java.util.List;

/**
 * Domain contract for the account service (synchronous gRPC).
 * Implementations translate transport failures into AccountUnavailableException.
 */
public interface AccountClient {

    /** True when accountRef belongs to userId. */
    boolean validateOwnership(String userId, String accountRef);

    /** The user's accounts. */
    List<AccountView> listAccounts(String userId);
}
