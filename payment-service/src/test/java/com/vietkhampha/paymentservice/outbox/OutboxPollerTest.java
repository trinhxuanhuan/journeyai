package com.vietkhampha.paymentservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietkhampha.paymentservice.entity.OutboxEvent;
import com.vietkhampha.paymentservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxPoller outboxPoller;

    @BeforeEach
    void setUp() {
        outboxPoller = new OutboxPoller(outboxEventRepository, kafkaTemplate, new ObjectMapper());
    }

    @Test
    void publishesStableEnvelopeUsingBookingIdAsPartitionKey() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-22T07:00:00Z");
        OutboxEvent event = event(
                eventId,
                paymentId,
                "payment.succeeded",
                occurredAt,
                """
                        {
                          "paymentId":"%s",
                          "bookingId":"%s",
                          "gateway":"VNPAY",
                          "amount":1250000.00,
                          "gatewayTransactionRef":"TEST_TXN_001"
                        }
                        """.formatted(paymentId, bookingId)
        );
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("payment-events"), eq(bookingId.toString()), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxPoller.pollAndPublish();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("payment-events"), eq(bookingId.toString()), messageCaptor.capture());
        Map<String, Object> message = messageCaptor.getValue();
        assertThat(message)
                .containsEntry("eventId", eventId.toString())
                .containsEntry("eventType", "payment.succeeded")
                .containsEntry("aggregateId", paymentId.toString())
                .containsEntry("occurredAt", occurredAt.toString());
        assertThat(message.get("payload")).isInstanceOfSatisfying(Map.class, payload -> {
            assertThat(payload)
                    .containsEntry("paymentId", paymentId.toString())
                    .containsEntry("bookingId", bookingId.toString())
                    .containsEntry("gateway", "VNPAY")
                    .containsEntry("gatewayTransactionRef", "TEST_TXN_001");
            assertThat(new BigDecimal(payload.get("amount").toString()))
                    .isEqualByComparingTo("1250000.00");
        });
        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository).save(event);
    }

    @Test
    void malformedPayloadRemainsUnpublishedForRetryOrOperationalIntervention() {
        OutboxEvent event = event(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payment.succeeded",
                Instant.parse("2026-08-22T07:00:00Z"),
                "{\"paymentId\":\"%s\"}".formatted(UUID.randomUUID())
        );
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        outboxPoller.pollAndPublish();

        assertThat(event.isPublished()).isFalse();
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    private OutboxEvent event(
            UUID eventId,
            UUID aggregateId,
            String eventType,
            Instant occurredAt,
            String payload
    ) {
        OutboxEvent event = new OutboxEvent("PAYMENT", aggregateId, eventType, payload);
        ReflectionTestUtils.setField(event, "id", eventId);
        ReflectionTestUtils.setField(event, "createdAt", occurredAt);
        return event;
    }
}
