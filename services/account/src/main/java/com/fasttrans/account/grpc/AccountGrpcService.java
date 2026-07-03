package com.fasttrans.account.grpc;

import com.fasttrans.account.entity.AccountEntity;
import com.fasttrans.account.repository.AccountRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * gRPC server — ValidateOwnership + ListAccounts.
 * Read-only, no write @Transactional needed. Uses default JPA reads.
 */
@GrpcService
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AccountGrpcService.class);

    private final AccountRepository accountRepository;

    public AccountGrpcService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
            var accountOpt = accountRepository.findByAccountRef(request.getAccountRef());
            if (accountOpt.isPresent()) {
                UUID requestedUserId = UUID.fromString(request.getUserId());
                owned = requestedUserId.equals(accountOpt.get().getUserId());
            }
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
        List<AccountEntity> accounts;
        try {
            UUID userId = UUID.fromString(request.getUserId());
            accounts = accountRepository.findByUserId(userId);
        } catch (Exception e) {
            log.error("ListAccounts error userId={}: {}", request.getUserId(), e.getMessage());
            responseObserver.onNext(ListAccountsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        ListAccountsResponse.Builder responseBuilder = ListAccountsResponse.newBuilder();
        for (AccountEntity account : accounts) {
            responseBuilder.addAccounts(
                    Account.newBuilder()
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
