package com.fasttrans.account.infrastructure.grpc;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ListAccountsRequest;
import com.fasttrans.account.grpc.ListAccountsResponse;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * gRPC server — ValidateOwnership + ListAccounts.
 * Read-only, delegates to AccountQueryService. Swallows errors → owned=false / empty list.
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

    /**
     * Lists all accounts of userId.
     * Returns accountRef (public), does NOT return the internal UUID.
     */
    @Override
    public void listAccounts(ListAccountsRequest request,
                             StreamObserver<ListAccountsResponse> responseObserver) {
        List<Account> accounts;
        try {
            UUID userId = UUID.fromString(request.getUserId());
            accounts = accountQueryService.listAccounts(userId);
        } catch (Exception e) {
            log.error("ListAccounts error userId={}: {}", request.getUserId(), e.getMessage());
            responseObserver.onNext(ListAccountsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        ListAccountsResponse.Builder responseBuilder = ListAccountsResponse.newBuilder();
        for (Account account : accounts) {
            responseBuilder.addAccounts(
                    com.fasttrans.account.grpc.Account.newBuilder()
                            .setAccountRef(account.getAccountRef())
                            .setOwnerName(account.getOwnerName())
                            .setBalance(account.getBalance())
                            .setCurrency(account.getCurrency())
                            .build()
            );
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}
