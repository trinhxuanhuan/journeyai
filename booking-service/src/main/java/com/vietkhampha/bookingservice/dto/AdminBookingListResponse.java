package com.vietkhampha.bookingservice.dto;

import java.util.List;

public class AdminBookingListResponse {
    private List<AdminBookingItemDto> items;
    private long total;
    private int page;

    public AdminBookingListResponse(List<AdminBookingItemDto> items, long total, int page) {
        this.items = items;
        this.total = total;
        this.page = page;
    }

    public List<AdminBookingItemDto> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
}