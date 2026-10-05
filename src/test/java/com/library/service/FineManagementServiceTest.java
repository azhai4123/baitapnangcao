package com.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.entity.Borrowing;
import com.library.entity.FineStatus;
import com.library.entity.Member;
import com.library.entity.MemberStatus;
import com.library.repository.BorrowingRepository;
import com.library.repository.FinePaymentRepository;
import com.library.repository.FinePolicyRepository;
import com.library.repository.FineWaiverRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FineManagementServiceTest {

    @Mock
    private BorrowingRepository borrowingRepository;

    @Mock
    private FinePaymentRepository paymentRepository;

    @Mock
    private FinePolicyRepository policyRepository;

    @Mock
    private FineWaiverRepository waiverRepository;

    private FineManagementService service;
    private Borrowing borrowing;

    @BeforeEach
    void setUp() {
        service = new FineManagementService(
                borrowingRepository,
                paymentRepository,
                policyRepository,
                waiverRepository,
                Clock.fixed(Instant.parse("2026-10-11T09:00:00Z"), ZoneOffset.UTC));
        borrowing = new Borrowing(
                new Member("Test Member", "member@example.test", MemberStatus.ACTIVE),
                LocalDate.of(2026, 10, 1).atStartOfDay(),
                LocalDate.of(2026, 10, 10),
                null);
        borrowing.addFine(BigDecimal.valueOf(10_000));
        when(borrowingRepository.lockById(1L)).thenReturn(Optional.of(borrowing));
    }

    @Test
    void recordsPartialPaymentAndUpdatesOutstandingBalance() {
        service.recordPayment(1L, BigDecimal.valueOf(3_000), "CASH", null);

        assertEquals(BigDecimal.valueOf(3_000), borrowing.getPaidFineAmount());
        assertEquals(BigDecimal.valueOf(7_000), borrowing.getUnpaidFineAmount());
        verify(paymentRepository).save(any());
        verify(borrowingRepository).flush();
    }

    @Test
    void rejectsPaymentAboveOutstandingBalanceWithoutRecordingPayment() {
        assertThrows(BorrowingException.class,
                () -> service.recordPayment(1L, BigDecimal.valueOf(10_001), "CASH", null));

        assertEquals(BigDecimal.valueOf(10_000), borrowing.getUnpaidFineAmount());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void marksFinePaidAfterFinalPayment() {
        service.recordPayment(1L, BigDecimal.valueOf(10_000), "BANK_TRANSFER", null);

        assertEquals(BigDecimal.ZERO, borrowing.getUnpaidFineAmount());
        assertEquals(FineStatus.FINE_PAID, borrowing.getFineStatus());
    }
}