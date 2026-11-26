package com.casms.payment.controller;

import com.casms.booking.entity.Booking;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.payment.dto.response.InvoiceResponse;
import com.casms.payment.repository.OnboardAccountMemberRepository;
import com.casms.payment.service.InvoiceService;
import com.casms.payment.service.OnboardFinancialAccess;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InvoiceControllerTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final InvoiceController controller = new InvoiceController(invoices,
            new OnboardFinancialAccess(bookings, mock(BookingPassengerRepository.class),
                    mock(OnboardAccountMemberRepository.class)));

    @Test
    void passengerCanListStoredInvoicesForOwnBookingWithoutCreatingAnInvoice() {
        var actor = passenger();
        UUID bookingId = ownedBooking(actor);
        when(invoices.readExistingBookingInvoices(bookingId)).thenReturn(List.of());

        controller.getInvoicesForBooking(bookingId, actor);

        verify(invoices).readExistingBookingInvoices(bookingId);
        verifyNoMoreInteractions(invoices);
    }

    @Test
    void passengerCannotListInvoicesForAnotherBooking() {
        UUID bookingId = ownedBooking(passenger());

        assertThrows(AccessDeniedException.class,
                () -> controller.getInvoicesForBooking(bookingId, passenger()));
        verifyNoInteractions(invoices);
    }

    @Test
    void personalBookingOwnershipDoesNotAuthorizeAnAccountWideInvoice() {
        var actor = passenger();
        UUID bookingId = ownedBooking(actor);
        UUID invoiceId = UUID.randomUUID();
        when(invoices.getInvoiceDetail(invoiceId)).thenReturn(InvoiceResponse.builder()
                .invoiceId(invoiceId).bookingId(bookingId).onboardAccountId(UUID.randomUUID()).build());

        assertThrows(AccessDeniedException.class, () -> controller.getInvoiceDetail(invoiceId, actor));
    }

    private UUID ownedBooking(CasmsPrincipal owner) {
        var booking = new Booking();
        UUID bookingId = UUID.randomUUID();
        booking.setBookingId(bookingId);
        booking.setBookedByUser(owner.getUser());
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        return bookingId;
    }

    private CasmsPrincipal passenger() {
        var user = new User();
        user.setUserId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(EnumSet.of(Role.PASSENGER));
        return new CasmsPrincipal(user, null);
    }
}
