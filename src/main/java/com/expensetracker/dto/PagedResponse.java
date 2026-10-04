package com.expensetracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class PagedResponse<T> {
    private List<T> data;
    private int page;
    private int pageSize;
    private long totalItems;
    private int totalPages;

    public PagedResponse(List<T> data, int page, int pageSize, long totalItems, int totalPages) {
        this.data = data;
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }

    // getters (and setters if you need them for serialization)
}
