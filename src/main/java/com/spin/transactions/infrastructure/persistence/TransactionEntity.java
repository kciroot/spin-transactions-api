package com.spin.transactions.infrastructure.persistence;

import com.spin.transactions.domain.Transaction;
import com.spin.transactions.domain.TransactionStatus;
import com.spin.transactions.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * JPA mapping, kept separate from the domain record. Implements Persistable so inserts with an
 * assigned UUID do not trigger a SELECT before the INSERT (Spring Data would otherwise call merge()).
 */
@Entity
@Table(name = "transactions")
class TransactionEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TransactionType type;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "description", length = 255)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status;
    @Column(name = "provider_transaction_id", length = 64)
    private String providerTransactionId;
    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", length = 512)
    private String errorMessage;
    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity;

    protected TransactionEntity() {
        // required by JPA
    }

    static TransactionEntity from(Transaction t, boolean isNew) {
        TransactionEntity e = new TransactionEntity();
        e.id = t.id();
        e.accountId = t.accountId();
        e.type = t.type();
        e.amount = t.amount();
        e.currency = t.currency();
        e.description = t.description();
        e.status = t.status();
        e.providerTransactionId = t.providerTransactionId();
        e.balanceAfter = t.balanceAfter();
        e.errorCode = t.errorCode();
        e.errorMessage = t.errorMessage();
        e.idempotencyKey = t.idempotencyKey();
        e.createdAt = t.createdAt();
        e.updatedAt = t.updatedAt();
        e.newEntity = isNew;
        return e;
    }

    Transaction toDomain() {
        return new Transaction(id, accountId, type, amount, currency, description, status, providerTransactionId,
                balanceAfter, errorCode, errorMessage, idempotencyKey, createdAt, updatedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
