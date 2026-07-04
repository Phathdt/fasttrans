package com.fasttrans.transfer.infrastructure.web;

import com.fasttrans.transfer.application.dto.AccountResponse;
import com.fasttrans.transfer.application.dto.CreateTransferRequest;
import com.fasttrans.transfer.application.dto.CreateTransferResponse;
import com.fasttrans.transfer.application.dto.TransferResponse;
import com.fasttrans.transfer.application.services.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Traefik strips /api before routing here; the controller is mounted at /transfers and /accounts.
// The X-User-Id header is injected by the gateway (ForwardAuth), so it is hidden from the public spec.
@RestController
@Tag(name = "transfers", description = "Money transfers and the current user's accounts")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * POST /transfers — create a new transfer (or idempotent replay).
     * X-User-Id header: injected by the gateway after ForwardAuth.
     * Idempotency-Key header: the client must provide it.
     */
    @Operation(summary = "Create a transfer (or idempotent replay)")
    @PostMapping("/transfers")
    public ResponseEntity<CreateTransferResponse> create(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userIdHeader,
            @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true,
                    description = "Client-generated key; replays return the same transfer")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest req) {

        UUID userId = UUID.fromString(userIdHeader);
        TransferResponse response = transferService.create(userId, idempotencyKey, req);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateTransferResponse(response.id(), response.status()));
    }

    /**
     * GET /transfers — the user's transfers, most recent first.
     */
    @Operation(summary = "List the current user's transfers, most recent first")
    @GetMapping("/transfers")
    public List<TransferResponse> list(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userIdHeader) {
        return transferService.list(UUID.fromString(userIdHeader));
    }

    /**
     * GET /transfers/{id} — details of a single transfer; 404 if it does not belong to the user.
     */
    @Operation(summary = "Get a single transfer by id")
    @GetMapping("/transfers/{id}")
    public TransferResponse detail(
            @PathVariable UUID id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userIdHeader) {
        return transferService.detail(id, UUID.fromString(userIdHeader));
    }

    /**
     * GET /accounts — proxies to the account service via gRPC; lets the FE pick a fromAccountRef.
     */
    @Operation(summary = "List the current user's accounts")
    @GetMapping("/accounts")
    public List<AccountResponse> accounts(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userIdHeader) {
        return transferService.listAccounts(UUID.fromString(userIdHeader));
    }
}
