package com.spin.transactions.infrastructure.persistence;

import com.spin.transactions.domain.DuplicateIdempotencyKeyException;
import com.spin.transactions.domain.PageResult;
import com.spin.transactions.domain.Transaction;
import com.spin.transactions.domain.TransactionQuery;
import com.spin.transactions.domain.TransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class JpaTransactionRepository implements TransactionRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final SpringDataTransactionRepository jpa;

    JpaTransactionRepository(SpringDataTransactionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Transaction create(Transaction transaction) {
        try {
            // saveAndFlush so a unique-constraint violation surfaces here and not at some later commit
            return jpa.saveAndFlush(TransactionEntity.from(transaction, true)).toDomain();
        } catch (DataIntegrityViolationException e) {
            if (transaction.idempotencyKey() != null) {
                throw new DuplicateIdempotencyKeyException(transaction.idempotencyKey(), e);
            }
            throw e;
        }
    }

    @Override
    public Transaction update(Transaction transaction) {
        return jpa.save(TransactionEntity.from(transaction, false)).toDomain();
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpa.findById(id).map(TransactionEntity::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey).map(TransactionEntity::toDomain);
    }

    @Override
    public PageResult<Transaction> search(TransactionQuery query) {
        PageRequest pageRequest = PageRequest.of(query.page() - 1, query.limit(), NEWEST_FIRST);
        Page<TransactionEntity> page = jpa.findAll(TransactionSpecifications.matching(query), pageRequest);
        return new PageResult<>(page.getContent().stream().map(TransactionEntity::toDomain).toList(),
                query.page(), query.limit(), page.getTotalElements());
    }
}
