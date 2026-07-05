package com.fasttrans.account.infrastructure.web;

import com.fasttrans.account.application.dto.AccountLookupResponse;
import com.fasttrans.account.application.dto.AccountResponse;
import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.domain.exception.AccountNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// X-User-Id header is injected by Traefik ForwardAuth; hidden from the public OpenAPI spec.
@RestController
@Tag(name = "accounts", description = "Account listing and lookup for the current user")
public class AccountController {

    private final AccountQueryService accountQueryService;

    public AccountController(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    /**
     * GET /accounts — list all accounts belonging to the authenticated user.
     * Balance included; X-User-Id is injected by the gateway and hidden from the spec.
     */
    @Operation(summary = "List the current user's accounts with balances", operationId = "listAccounts")
    @GetMapping("/accounts")
    public List<AccountResponse> listAccounts(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userIdHeader) {
        return accountQueryService.listAccounts(UUID.fromString(userIdHeader)).stream()
                .map(a -> new AccountResponse(a.getAccountRef(), a.getOwnerName(), a.getBalance(), a.getCurrency()))
                .toList();
    }

    /**
     * GET /accounts/{accountRef} — resolve a public account ref to owner name.
     * Balance is intentionally omitted (privacy). Returns 404 when ref does not exist.
     */
    @Operation(summary = "Look up an account by its public ref (no balance)", operationId = "lookupAccount")
    @GetMapping("/accounts/{accountRef}")
    public AccountLookupResponse lookupAccount(@PathVariable String accountRef) {
        return accountQueryService.lookup(accountRef)
                .map(a -> new AccountLookupResponse(a.getAccountRef(), a.getOwnerName()))
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountRef + " not found"));
    }
}
