package com.vietkhampha.bookingservice.dto;

import java.util.List;

public class CustomerBookingListResponse {

    private final List<CustomerBookingItemResponse> items;
    private final long total;
    private final int page;
    private final int size;
    private final int totalPages;

    public CustomerBookingListResponse(
            List<CustomerBookingItemResponse> items,
            long total,
            int page,
            int size,
            int totalPages
    ) {
        this.items = List.copyOf(items);
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
    }

    public List<CustomerBookingItemResponse> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getTotalPages() { return totalPages; }
}
