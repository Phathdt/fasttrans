package com.fasttrans.transfer.domain.interfaces;

/**
 * Domain contract for the account service (synchronous gRPC).
 * Implementations translate transport failures into AccountUnavailableException.
 */
public interface AccountClient {

    /** True when accountRef belongs to userId. */
    boolean validateOwnership(String userId, String accountRef);
}
