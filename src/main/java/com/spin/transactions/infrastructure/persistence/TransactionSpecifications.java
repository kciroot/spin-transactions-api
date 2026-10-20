package com.spin.transactions.infrastructure.persistence;

import com.spin.transactions.domain.TransactionQuery;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /** Builds the WHERE clause from the optional filters; null filters are ignored. */
    static Specification<TransactionEntity> matching(TransactionQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.accountId() != null) {
                predicates.add(cb.equal(root.get("accountId"), query.accountId()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.type() != null) {
                predicates.add(cb.equal(root.get("type"), query.type()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
