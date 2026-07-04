package com.fasttrans.account.infrastructure.grpc;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * gRPC server — ValidateOwnership only.
 * Read-only, delegates to AccountQueryService. Swallows errors → owned=false.
 */
@GrpcService
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AccountGrpcService.class);

    private final AccountQueryService accountQueryService;

    public AccountGrpcService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    /**
     * Checks whether accountRef belongs to userId.
     * Ref does not exist → owned = false (does not throw an error).
     */
    @Override
    public void validateOwnership(ValidateOwnershipRequest request,
                                  StreamObserver<ValidateOwnershipResponse> responseObserver) {
        boolean owned = false;
        try {
            UUID requestedUserId = UUID.fromString(request.getUserId());
            owned = accountQueryService.validateOwnership(request.getAccountRef(), requestedUserId);
        } catch (Exception e) {
            log.error("ValidateOwnership error accountRef={} userId={}: {}",
                    request.getAccountRef(), request.getUserId(), e.getMessage());
            // Return owned=false instead of throwing — the caller handles it gracefully
        }

        responseObserver.onNext(ValidateOwnershipResponse.newBuilder().setOwned(owned).build());
        responseObserver.onCompleted();
    }
}
