package com.vietkhampha.bookingservice.statemachine;

import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachineFactory
public class BookingStateMachineConfig extends EnumStateMachineConfigurerAdapter<BookingState, BookingEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<BookingState, BookingEvent> states) throws Exception {
        states
                .withStates()
                .initial(BookingState.PENDING)
                .states(java.util.EnumSet.allOf(BookingState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<BookingState, BookingEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(BookingState.PENDING).target(BookingState.CONFIRMED)
                .event(BookingEvent.PAYMENT_CONFIRMED)
                .and()
                .withExternal()
                .source(BookingState.PENDING).target(BookingState.PAYMENT_FAILED)
                .event(BookingEvent.PAYMENT_FAILED)
                .and()
                .withExternal()
                .source(BookingState.PENDING).target(BookingState.EXPIRED)
                .event(BookingEvent.HOLD_TIMEOUT)
                .and()
                .withExternal()
                .source(BookingState.PENDING).target(BookingState.CANCELLED)
                .event(BookingEvent.CUSTOMER_CANCEL)
                .and()
                .withExternal()
                .source(BookingState.CONFIRMED).target(BookingState.CANCELLED)
                .event(BookingEvent.CUSTOMER_CANCEL)
                .and()
                .withExternal()
                .source(BookingState.CONFIRMED).target(BookingState.COMPLETED)
                .event(BookingEvent.TOUR_COMPLETED);
    }
}
