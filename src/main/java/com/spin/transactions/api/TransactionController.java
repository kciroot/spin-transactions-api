package com.spin.transactions.api;

import com.spin.transactions.api.dto.CreateTransactionRequest;
import com.spin.transactions.api.dto.PageResponse;
import com.spin.transactions.api.dto.TransactionResponse;
import com.spin.transactions.application.ExecuteTransactionCommand;
import com.spin.transactions.application.ExecutionResult;
import com.spin.transactions.application.TransactionService;
import com.spin.transactions.domain.Transaction;
import com.spin.transactions.domain.TransactionQuery;
import com.spin.transactions.domain.TransactionStatus;
import com.spin.transactions.domain.TransactionType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    static final int MAX_LIMIT = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * 201 EXECUTED / REJECTED (the transaction was recorded), 502 FAILED (provider unavailable, outcome unknown),
     * 200 when an Idempotency-Key replays an existing transaction, 422 business rule violation.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be between 1 and " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        ExecutionResult result = service.execute(new ExecuteTransactionCommand(body.accountId(), body.type(),
                body.amount(), body.currency(), body.description(), idempotencyKey));

        Transaction transaction = result.transaction();
        TransactionResponse response = TransactionResponse.from(transaction);
        if (result.replayed()) {
            return ResponseEntity.ok(response);
        }
        if (transaction.status() == TransactionStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }
        return ResponseEntity.created(URI.create("/transactions/" + transaction.id())).body(response);
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        if (page < 1) {
            throw new InvalidRequestException("page must be >= 1");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return PageResponse.from(
                service.search(new TransactionQuery(accountId, status, type, page, limit))
                        .map(TransactionResponse::from));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.from(service.getById(id));
    }
}
