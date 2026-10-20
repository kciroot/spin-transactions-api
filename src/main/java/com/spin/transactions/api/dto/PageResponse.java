package com.spin.transactions.api.dto;

import com.spin.transactions.domain.PageResult;
import java.util.List;

public record PageResponse<T>(List<T> data, int page, int limit, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.items(), result.page(), result.limit(), result.totalElements(),
                result.totalPages());
    }
}
