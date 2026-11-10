package com.casms.notification.service;

import com.casms.booking.entity.Booking;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.ship.entity.Cruise;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingEmailTest {
    @Test void confirmationReplayDoesNotGenerateAnotherEmailOrResetToken() {
        var jdbc = mock(JdbcTemplate.class);
        var booking = booking();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true);
        new BookingEmailService(jdbc, mock(PasswordEncoder.class)).enqueueConfirmation(booking, BigDecimal.TEN);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void existingAccountConfirmationContainsNoPasswordSetup() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new BookingEmailService(jdbc, mock(PasswordEncoder.class));
        ReflectionTestUtils.setField(service, "portalUrl", "https://portal.example");
        var booking = booking();
        service.enqueueConfirmation(booking, BigDecimal.TEN);
        verify(jdbc).update(contains("INSERT INTO \"BookingEmailOutbox\""),
                eq("BOOKING_CONFIRMED:" + booking.getBookingId()), eq(booking.getBookingId()),
                eq("owner@example.com"), anyString(), argThat((Object body) -> !body.toString().contains("#token=")));
        verify(jdbc, never()).update(contains("UPDATE \"BookingAccountSetup\""), any(), any(), any());
    }

    @Test void expiredOrReusedSetupTokenNeverChangesPassword() {
        var jdbc = mock(JdbcTemplate.class);
        var encoder = mock(PasswordEncoder.class);
        var service = new BookingEmailService(jdbc, encoder);
        assertEquals("ACCOUNT_SETUP_INVALID", assertThrows(BusinessException.class,
                () -> service.activate("expired-token", "a-long-new-password")).getCode());
        verifyNoInteractions(encoder);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void utf8PasswordCannotExceedBcryptByteLimit() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new BookingEmailService(jdbc, mock(PasswordEncoder.class));
        assertEquals("PASSWORD_TOO_LONG", assertThrows(BusinessException.class,
                () -> service.activate("token", "\u20ac".repeat(30))).getCode());
        verifyNoInteractions(jdbc);
    }

    @Test void smtpFailureSchedulesRetryAndDoesNotMarkSent() {
        var jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
                "event_key", "key", "recipient", "owner@example.com", "subject", "Confirmed", "body", "Booking")));
        doThrow(new MailSendException("unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var worker = new BookingEmailDispatcher(jdbc, provider);
        ReflectionTestUtils.setField(worker, "from", "sender@example.com");
        worker.dispatch();
        verify(jdbc).update(contains("next_attempt_at = now() + interval '5 minutes'"), eq("key"));
        verify(jdbc, never()).update(contains("SET sent_at"), eq("key"));
    }

    @Test void smtpSuccessMarksCommittedMessageSent() {
        var jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
                "event_key", "key", "recipient", "owner@example.com", "subject", "Confirmed", "body", "Booking")));
        var worker = new BookingEmailDispatcher(jdbc, provider);
        ReflectionTestUtils.setField(worker, "from", "sender@example.com");
        worker.dispatch();
        verify(sender).send(any(SimpleMailMessage.class));
        verify(jdbc).update(contains("SET sent_at"), eq("key"));
    }

    private Booking booking() {
        var booking = new Booking(); booking.setBookingId(UUID.randomUUID());
        booking.setAgreedFareAmount(new BigDecimal("100")); booking.setCurrency("USD");
        var owner = new User(); owner.setUserId(UUID.randomUUID());
        owner.setEmail("owner@example.com"); owner.setUsername("owner"); booking.setBookedByUser(owner);
        booking.setCruise(new Cruise()); return booking;
    }
}
