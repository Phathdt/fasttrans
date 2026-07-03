package com.fasttrans.transfer.controller;

import com.fasttrans.transfer.dto.AccountResponse;
import com.fasttrans.transfer.dto.CreateTransferRequest;
import com.fasttrans.transfer.dto.TransferResponse;
import com.fasttrans.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Traefik strips /api before routing here; the controller is mounted at /transfers and /accounts.
@RestController
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
    @PostMapping("/transfers")
    public ResponseEntity<Map<String, String>> create(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest req) {

        UUID userId = UUID.fromString(userIdHeader);
        TransferResponse response = transferService.create(userId, idempotencyKey, req);

        // 201 {id, status}
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", response.id(), "status", response.status()));
    }

    /**
     * GET /transfers — the user's transfers, most recent first.
     */
    @GetMapping("/transfers")
    public List<TransferResponse> list(@RequestHeader("X-User-Id") String userIdHeader) {
        return transferService.list(UUID.fromString(userIdHeader));
    }

    /**
     * GET /transfers/{id} — details of a single transfer; 404 if it does not belong to the user.
     */
    @GetMapping("/transfers/{id}")
    public TransferResponse detail(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userIdHeader) {
        return transferService.detail(id, UUID.fromString(userIdHeader));
    }

    /**
     * GET /accounts — proxies to the account service via gRPC; lets the FE pick a fromAccountRef.
     */
    @GetMapping("/accounts")
    public List<AccountResponse> accounts(@RequestHeader("X-User-Id") String userIdHeader) {
        return transferService.listAccounts(UUID.fromString(userIdHeader));
    }
}
