package com.vietkhampha.tourservice.dto;

import java.util.List;

public class TourSearchResponse {

    private List<TourSearchItemDto> items;
    private long total;
    private int page;

    public TourSearchResponse(List<TourSearchItemDto> items, long total, int page) {
        this.items = items;
        this.total = total;
        this.page = page;
    }

    public List<TourSearchItemDto> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
}