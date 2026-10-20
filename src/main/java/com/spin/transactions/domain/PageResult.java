package com.spin.transactions.domain;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int limit, long totalElements) {

    public int totalPages() {
        return (int) Math.ceil((double) totalElements / limit);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, limit, totalElements);
    }
}
