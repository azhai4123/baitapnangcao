package com.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.dto.BorrowingFormDTO;
import com.library.dto.BorrowingItemDTO;
import com.library.dto.ReturnFormDTO;
import com.library.entity.Book;
import com.library.entity.Borrowing;
import com.library.entity.BorrowingDetail;
import com.library.entity.BorrowingStatus;
import com.library.entity.Category;
import com.library.entity.FinePolicy;
import com.library.entity.Member;
import com.library.entity.MemberStatus;
import com.library.mapper.BorrowingMapper;
import com.library.repository.BookRepository;
import com.library.repository.BorrowingDetailRepository;
import com.library.repository.BorrowingRepository;
import com.library.repository.FinePolicyRepository;
import com.library.repository.MemberRepository;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BorrowingServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-11T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BorrowingRepository borrowingRepository;

    @Mock
    private BorrowingDetailRepository detailRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

        @Mock
        private FinePolicyRepository finePolicyRepository;

    private BorrowingService service;

    @BeforeEach
    void setUp() {
        service = new BorrowingService(
                borrowingRepository,
                detailRepository,
                bookRepository,
                memberRepository,
                finePolicyRepository,
                new BorrowingMapper(),
                new FineCalculationService(),
                FIXED_CLOCK);
    }

    @Test
    void createsLoanAndReducesInventoryByRequestedCopies() {
        Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
        Book book = new Book(new Category("Fiction", null), "9780000000001",
                "Test Book", "Test Author", null, null, 3, 3);
        when(memberRepository.findByIdForBorrowing(1L)).thenReturn(Optional.of(member));
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));
        when(borrowingRepository.save(any(Borrowing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(detailRepository.save(any(BorrowingDetail.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BorrowingFormDTO form = new BorrowingFormDTO();
        form.setMemberId(1L);
        form.setDueDate(LocalDate.of(2026, 10, 25));
        form.setItems(List.of(item(2L, 2)));

        var response = service.createBorrowing(form);

        assertEquals(LocalDate.of(2026, 10, 25), response.dueDate());
        assertEquals(1, response.items().size());
        assertEquals(2, response.items().get(0).quantity());
        assertEquals(1, book.getAvailableQuantity());
        verify(detailRepository).save(any(BorrowingDetail.class));
    }

    @Test
    void rejectsLockedMemberWithoutChangingInventory() {
        Member member = new Member("Locked Member", "locked@example.test", MemberStatus.LOCKED);
        when(memberRepository.findByIdForBorrowing(1L)).thenReturn(Optional.of(member));
        BorrowingFormDTO form = new BorrowingFormDTO();
        form.setMemberId(1L);
        form.setItems(List.of(item(2L, 1)));

        assertThrows(BorrowingException.class, () -> service.createBorrowing(form));

        verify(borrowingRepository, never()).save(any(Borrowing.class));
        verify(bookRepository, never()).findById(2L);
    }

    @Test
    void rejectsBorrowingMoreThanAvailableWithoutCreatingBorrowing() {
        Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
        Book book = new Book(new Category("Fiction", null), "9780000000001",
                "Test Book", "Test Author", null, null, 1, 1);
        when(memberRepository.findByIdForBorrowing(1L)).thenReturn(Optional.of(member));
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));
        BorrowingFormDTO form = new BorrowingFormDTO();
        form.setMemberId(1L);
        form.setDueDate(LocalDate.of(2026, 10, 25));
        form.setItems(List.of(item(2L, 2)));

        assertThrows(BorrowingException.class, () -> service.createBorrowing(form));

        assertEquals(1, book.getAvailableQuantity());
        verify(borrowingRepository, never()).save(any(Borrowing.class));
    }

        @Test
        void leavesEarlierBookInventoryUntouchedWhenAnotherBookIsUnavailable() {
                Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
                Book firstBook = new Book(new Category("Fiction", null), "9780000000003",
                                "First Book", "Test Author", null, null, 2, 2);
                Book unavailableBook = new Book(new Category("Fiction", null), "9780000000004",
                                "Unavailable Book", "Test Author", null, null, 1, 0);
                when(memberRepository.findByIdForBorrowing(1L)).thenReturn(Optional.of(member));
                when(bookRepository.findById(2L)).thenReturn(Optional.of(firstBook));
                when(bookRepository.findById(3L)).thenReturn(Optional.of(unavailableBook));

                BorrowingFormDTO form = new BorrowingFormDTO();
                form.setMemberId(1L);
                form.setDueDate(LocalDate.of(2026, 10, 25));
                form.setItems(List.of(item(2L, 1), item(3L, 1)));

                assertThrows(BorrowingException.class, () -> service.createBorrowing(form));

                assertEquals(2, firstBook.getAvailableQuantity());
                verify(borrowingRepository, never()).save(any(Borrowing.class));
        }

        @Test
        void rejectsBorrowingThatWouldExceedFiveOutstandingCopies() {
                Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
                when(memberRepository.findByIdForBorrowing(1L)).thenReturn(Optional.of(member));
                when(borrowingRepository.countOutstandingCopies(null)).thenReturn(4L);
                BorrowingFormDTO form = new BorrowingFormDTO();
                form.setMemberId(1L);
                form.setDueDate(LocalDate.of(2026, 10, 25));
                form.setItems(List.of(item(2L, 2)));

                assertThrows(BorrowingException.class, () -> service.createBorrowing(form));

                verify(bookRepository, never()).findById(2L);
        }

        @Test
        void blocksNewBorrowingWhenMemberHasUnpaidFine() {
                Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
                ReflectionTestUtils.setField(member, "id", 1L);
                when(memberRepository.findByIdForBorrowing(1L)).thenReturn(Optional.of(member));
                when(borrowingRepository.existsByMember_IdAndUnpaidFineAmountGreaterThan(
                                1L, BigDecimal.ZERO)).thenReturn(true);
                BorrowingFormDTO form = new BorrowingFormDTO();
                form.setMemberId(1L);
                form.setDueDate(LocalDate.of(2026, 10, 25));
                form.setItems(List.of(item(2L, 1)));

                assertThrows(BorrowingException.class, () -> service.createBorrowing(form));

                verify(bookRepository, never()).findById(2L);
                verify(borrowingRepository, never()).save(any(Borrowing.class));
        }

    @Test
    void returnsCopyAndCalculatesOneDayFine() {
        Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "id", 1L);
        Book book = new Book(new Category("Fiction", null), "9780000000001",
                "Test Book", "Test Author", null, null, 1, 0);
        ReflectionTestUtils.setField(book, "id", 2L);
        Borrowing borrowing = new Borrowing(
                member,
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                LocalDate.of(2026, 10, 10),
                null);
        ReflectionTestUtils.setField(borrowing, "id", 3L);
        BorrowingDetail detail = new BorrowingDetail(borrowing, book);
        ReflectionTestUtils.setField(detail, "id", 4L);
        when(borrowingRepository.findDetailedById(3L)).thenReturn(Optional.of(borrowing));
        when(detailRepository.findByIdIn(anyCollection())).thenReturn(List.of(detail));
        when(finePolicyRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(new FinePolicy(BigDecimal.valueOf(5_000), null, 0, true)));

        ReturnFormDTO form = new ReturnFormDTO();
        form.setBorrowingDetailIds(List.of(4L));

        service.returnBooks(3L, form);

        assertEquals(1, book.getAvailableQuantity());
        assertEquals(1, detail.getOverdueDays());
        assertEquals(5_000, detail.getFineAmount().longValueExact());
        assertEquals(BorrowingStatus.RETURNED, borrowing.getStatus());
        assertEquals(BigDecimal.valueOf(5_000), borrowing.getUnpaidFineAmount());
        verify(detailRepository).flush();
        verify(bookRepository).flush();
    }

    @Test
    void partiallyReturnsQuantityAndChargesOnlyThatQuantity() {
        Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
        Book book = new Book(new Category("Fiction", null), "9780000000002",
                "Test Book", "Test Author", null, null, 3, 0);
        Borrowing borrowing = new Borrowing(
                member,
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                LocalDate.of(2026, 10, 10),
                null);
        BorrowingDetail detail = new BorrowingDetail(borrowing, book, 3);
        borrowing.addDetail(detail);
        ReflectionTestUtils.setField(borrowing, "id", 5L);
        ReflectionTestUtils.setField(detail, "id", 6L);
        when(borrowingRepository.findDetailedById(5L)).thenReturn(Optional.of(borrowing));
        when(detailRepository.findByIdIn(anyCollection())).thenReturn(List.of(detail));
        when(finePolicyRepository.findFirstByActiveTrueOrderByIdAsc())
                .thenReturn(Optional.of(new FinePolicy(BigDecimal.valueOf(5_000), null, 0, true)));

        ReturnFormDTO form = new ReturnFormDTO();
        form.setReturnedQuantities(java.util.Map.of(6L, 1));

        service.returnBooks(5L, form);

        assertEquals(1, detail.getReturnedQuantity());
        assertEquals(null, detail.getReturnedAt());
        assertEquals(1, book.getAvailableQuantity());
        assertEquals(5_000, detail.getFineAmount().longValueExact());
        assertEquals(BorrowingStatus.OVERDUE, borrowing.getStatus());
    }

        @Test
        void cancelsUnreturnedBorrowingAndRestoresAllCopies() {
                Member member = new Member("Test Member", "member@example.test", MemberStatus.ACTIVE);
                Book book = new Book(new Category("Fiction", null), "9780000000005",
                                "Test Book", "Test Author", null, null, 2, 0);
                Borrowing borrowing = new Borrowing(
                                member,
                                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                                LocalDate.of(2026, 10, 25),
                                null);
                borrowing.addDetail(new BorrowingDetail(borrowing, book, 2));
                when(borrowingRepository.findDetailedById(7L)).thenReturn(Optional.of(borrowing));

                service.cancelBorrowing(7L);

                assertEquals(BorrowingStatus.CANCELLED, borrowing.getStatus());
                assertEquals(2, book.getAvailableQuantity());
                verify(bookRepository).flush();
                verify(borrowingRepository).flush();
        }

    private BorrowingItemDTO item(Long bookId, int quantity) {
        BorrowingItemDTO item = new BorrowingItemDTO();
        item.setBookId(bookId);
        item.setQuantity(quantity);
        return item;
    }
}
