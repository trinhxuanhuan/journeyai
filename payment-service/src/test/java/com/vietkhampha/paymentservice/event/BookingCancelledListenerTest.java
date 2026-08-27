package com.vietkhampha.paymentservice.event;

import com.vietkhampha.paymentservice.repository.ProcessedBookingEventRepository;
import com.vietkhampha.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingCancelledListenerTest {

    @Test
    void replayedCancellationEventCreatesOnlyOneRefundRequest() {
        PaymentService paymentService = mock(PaymentService.class);
        ProcessedBookingEventRepository inbox = mock(ProcessedBookingEventRepository.class);
        BookingCancelledListener listener = new BookingCancelledListener(paymentService, inbox);
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "eventId", eventId.toString(),
                "eventType", "booking.cancelled",
                "payload", Map.of(
                        "bookingId", bookingId.toString(),
                        "refundEligible", true,
                        "refundPercentage", 80
                )
        );
        when(inbox.tryClaim(eventId, bookingId, "booking.cancelled")).thenReturn(true, false);

        listener.handleBookingEvent(event);
        listener.handleBookingEvent(event);

        verify(paymentService, times(1)).processRefund(bookingId, 80, "127.0.0.1");
    }
}
