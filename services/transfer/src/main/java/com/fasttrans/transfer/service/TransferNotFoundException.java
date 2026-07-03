package com.fasttrans.transfer.service;

// Transfer not found or does not belong to the user → 404.
public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String message) {
        super(message);
    }
}
