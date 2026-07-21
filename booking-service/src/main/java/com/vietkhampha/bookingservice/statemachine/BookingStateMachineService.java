package com.vietkhampha.bookingservice.statemachine;

import com.vietkhampha.bookingservice.entity.Booking;
import com.vietkhampha.bookingservice.exception.BusinessException;
import com.vietkhampha.bookingservice.exception.ErrorCode;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
@Service
public class BookingStateMachineService {

    private final StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;

    public BookingStateMachineService(StateMachineFactory<BookingState, BookingEvent> stateMachineFactory) {
        this.stateMachineFactory = stateMachineFactory;
    }

    public void transition(Booking booking, BookingEvent event) {
        StateMachine<BookingState, BookingEvent> sm = stateMachineFactory.getStateMachine();

        sm.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachine(new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                        BookingState.valueOf(booking.getStatus().name()), null, null, null))
        );
        sm.start();

        boolean accepted = sm.sendEvent(MessageBuilder.withPayload(event).build());

        if (!accepted) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        BookingState newState = sm.getState().getId();
        booking.setStatus(Booking.Status.valueOf(newState.name()));
        sm.stop();
    }
}