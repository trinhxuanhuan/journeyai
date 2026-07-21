package com.vietkhampha.bookingservice.statemachine;

public enum BookingEvent {
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    HOLD_TIMEOUT,
    CUSTOMER_CANCEL,
    TOUR_COMPLETED
}