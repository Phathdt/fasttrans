package com.fasttrans.transfer.infrastructure.grpc;

import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ListAccountsRequest;
import com.fasttrans.account.grpc.ListAccountsResponse;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import com.fasttrans.transfer.domain.entities.AccountView;
import com.fasttrans.transfer.domain.exception.AccountUnavailableException;
import com.fasttrans.transfer.domain.interfaces.AccountClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC adapter to account:9090 implementing the domain AccountClient.
 * 5s deadline (decided in Validation Session 1). Transport failures
 * (UNAVAILABLE/DEADLINE_EXCEEDED and others) are mapped to AccountUnavailableException.
 */
@Component
public class AccountGrpcClient implements AccountClient {

    private static final long DEADLINE_SECONDS = 5;

    @GrpcClient("account")
    private AccountServiceGrpc.AccountServiceBlockingStub stub;

    @Override
    public boolean validateOwnership(String userId, String accountRef) {
        try {
            ValidateOwnershipResponse resp = stub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .validateOwnership(ValidateOwnershipRequest.newBuilder()
                            .setUserId(userId)
                            .setAccountRef(accountRef)
                            .build());
            return resp.getOwned();
        } catch (StatusRuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    @Override
    public List<AccountView> listAccounts(String userId) {
        try {
            ListAccountsResponse response = stub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .listAccounts(ListAccountsRequest.newBuilder()
                            .setUserId(userId)
                            .build());
            return response.getAccountsList().stream()
                    .map(a -> new AccountView(
                            a.getAccountRef(),
                            a.getOwnerName(),
                            a.getBalance(),
                            a.getCurrency()))
                    .toList();
        } catch (StatusRuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    private AccountUnavailableException mapUnavailable(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
            return new AccountUnavailableException("Account service unavailable", e);
        }
        return new AccountUnavailableException("gRPC error: " + e.getStatus(), e);
    }
}
