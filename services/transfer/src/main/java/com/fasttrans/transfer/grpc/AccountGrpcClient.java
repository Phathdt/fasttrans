package com.fasttrans.transfer.grpc;

import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ListAccountsRequest;
import com.fasttrans.account.grpc.ListAccountsResponse;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// Wraps the blocking stub to account:9090. 5s deadline (decided in Validation Session 1).
@Component
public class AccountGrpcClient {

    private static final long DEADLINE_SECONDS = 5;

    @GrpcClient("account")
    private AccountServiceGrpc.AccountServiceBlockingStub stub;

    public boolean validateOwnership(String userId, String accountRef) {
        ValidateOwnershipResponse resp = stub
                .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .validateOwnership(ValidateOwnershipRequest.newBuilder()
                        .setUserId(userId)
                        .setAccountRef(accountRef)
                        .build());
        return resp.getOwned();
    }

    public ListAccountsResponse listAccounts(String userId) {
        return stub
                .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .listAccounts(ListAccountsRequest.newBuilder()
                        .setUserId(userId)
                        .build());
    }
}
