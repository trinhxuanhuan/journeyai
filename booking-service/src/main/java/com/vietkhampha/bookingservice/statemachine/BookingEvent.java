package com.vietkhampha.bookingservice.statemachine;

public enum BookingEvent {
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    HOLD_TIMEOUT,
    CUSTOMER_CANCEL,
    TOUR_COMPLETED,
    LATE_PAYMENT_RECOVERED,
    LATE_PAYMENT_REVIEW
}