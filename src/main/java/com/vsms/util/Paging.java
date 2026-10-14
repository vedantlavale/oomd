package com.vsms.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Paging {

    public static final int PAGE_SIZE = 10;

    private Paging() {
    }

    public static Pageable of(int page, Sort sort) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort);
    }

    public static Pageable of(int page) {
        return of(page, Sort.unsorted());
    }
}
