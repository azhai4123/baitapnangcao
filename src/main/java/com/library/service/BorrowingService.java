package com.library.service;

import com.library.dto.BorrowingFormDTO;
import com.library.dto.BorrowingItemDTO;
import com.library.dto.BorrowingResponseDTO;
import com.library.dto.OverdueBookResponseDTO;
import com.library.dto.ReturnFormDTO;
import com.library.dto.ReturnLineResponseDTO;
import com.library.dto.ReturnPreviewResponseDTO;
import com.library.entity.Book;
import com.library.entity.Borrowing;
import com.library.entity.BorrowingDetail;
import com.library.entity.BorrowingStatus;
import com.library.entity.FinePolicy;
import com.library.entity.Member;
import com.library.mapper.BorrowingMapper;
import com.library.repository.BookRepository;
import com.library.repository.BorrowingDetailRepository;
import com.library.repository.BorrowingRepository;
import com.library.repository.FinePolicyRepository;
import com.library.repository.MemberRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class BorrowingService {

    private final BorrowingRepository borrowingRepository;
    private final BorrowingDetailRepository detailRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final FinePolicyRepository finePolicyRepository;
    private final BorrowingMapper borrowingMapper;
    private final FineCalculationService fineCalculationService;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public BorrowingResponseDTO createBorrowing(@Valid BorrowingFormDTO form) {
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new BorrowingException("Phiếu mượn phải có ít nhất một cuốn sách.");
        }

        Member member = memberRepository.findByIdForBorrowing(form.getMemberId())
                .orElseThrow(() -> new BorrowingNotFoundException("Không tìm thấy thành viên."));
        if (!member.canBorrow()) {
            throw new BorrowingException("Thành viên đang bị khóa và không thể mượn sách.");
        }
        LocalDate today = LocalDate.now(clock);
        if (borrowingRepository.hasOverdueBooks(member.getId(), today)) {
            throw new BorrowingException("Thành viên đang có sách quá hạn và chưa thể mượn thêm.");
        }
        if (borrowingRepository.existsByMember_IdAndUnpaidFineAmountGreaterThan(
                member.getId(), BigDecimal.ZERO)) {
            throw new BorrowingException("Thành viên còn phí phạt chưa thanh toán.");
        }

        Map<Long, Integer> quantitiesByBook = aggregateItems(form.getItems());
        long requestedCopies = quantitiesByBook.values().stream().mapToLong(Integer::longValue).sum();
        if (borrowingRepository.countOutstandingCopies(member.getId()) + requestedCopies > 5) {
            throw new BorrowingException("Mỗi thành viên chỉ được giữ tối đa 5 cuốn chưa trả.");
        }
        Map<Long, Book> booksById = new HashMap<>();
        for (Map.Entry<Long, Integer> item : quantitiesByBook.entrySet()) {
            Book book = bookRepository.findById(item.getKey())
                    .orElseThrow(() -> new BorrowingNotFoundException("Không tìm thấy sách đã chọn."));
            if (book.getAvailableQuantity() < item.getValue()) {
                throw new BorrowingException(
                        "Sách \"" + book.getTitle() + "\" không đủ số lượng khả dụng.");
            }
            booksById.put(item.getKey(), book);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (form.getDueDate() == null || form.getDueDate().isBefore(today)) {
            throw new BorrowingException("Hạn trả phải từ hôm nay trở đi.");
        }
        Borrowing borrowing = new Borrowing(member, now, form.getDueDate(), form.getNotes());
        borrowingRepository.save(borrowing);

        for (Map.Entry<Long, Integer> item : quantitiesByBook.entrySet()) {
            Book book = booksById.get(item.getKey());
            book.borrowCopies(item.getValue());
            BorrowingDetail detail = new BorrowingDetail(borrowing, book, item.getValue());
            borrowing.addDetail(detail);
            detailRepository.save(detail);
        }

        try {
            bookRepository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new BorrowingConflictException(
                    "Tồn kho vừa thay đổi. Tải lại dữ liệu sách rồi thử lại.");
        }
        return borrowingMapper.toResponse(borrowing, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public ReturnPreviewResponseDTO previewReturn(Long borrowingId, @Valid ReturnFormDTO form) {
        Borrowing borrowing = findDetailedBorrowing(borrowingId);
        if (borrowing.getStatus() == BorrowingStatus.CANCELLED
                || borrowing.getStatus() == BorrowingStatus.RETURNED) {
            throw new BorrowingException("Không thể trả sách cho phiếu đã hủy hoặc đã hoàn tất.");
        }
        Map<Long, Integer> quantities = selectedQuantities(form);
        List<BorrowingDetail> details = findSelectedDetails(borrowingId, quantities);
        return buildReturnPreview(borrowing, details, quantities, LocalDate.now(clock));
    }

    @Transactional(rollbackFor = Exception.class)
    public BorrowingResponseDTO returnBooks(Long borrowingId, @Valid ReturnFormDTO form) {
        Borrowing borrowing = findDetailedBorrowing(borrowingId);
        if (borrowing.getStatus() == BorrowingStatus.CANCELLED
            || borrowing.getStatus() == BorrowingStatus.RETURNED) {
            throw new BorrowingException("Không thể trả sách cho phiếu đã hủy hoặc đã hoàn tất.");
        }
        Map<Long, Integer> quantities = selectedQuantities(form);
        List<BorrowingDetail> details = findSelectedDetails(borrowingId, quantities);
        LocalDateTime returnedAt = LocalDateTime.now(clock);
        ReturnPreviewResponseDTO preview =
            buildReturnPreview(borrowing, details, quantities, returnedAt.toLocalDate());

        for (BorrowingDetail detail : details) {
            Book book = detail.getBook();
            ReturnLineResponseDTO line = preview.lines().stream()
                    .filter(candidate -> candidate.detailId().equals(detail.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BorrowingException("Không thể tính khoản phạt cho sách."));
            int returnedQuantity = line.returnedQuantity();
            detail.markReturned(returnedQuantity, returnedAt, line.overdueDays(), line.fineAmount());
            book.returnCopies(returnedQuantity);
            borrowing.addFine(line.fineAmount());
        }
        borrowing.refreshStatus(returnedAt.toLocalDate());

        try {
            detailRepository.flush();
            bookRepository.flush();
            borrowingRepository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new BorrowingConflictException(
                    "Trạng thái trả hoặc tồn kho vừa thay đổi. Tải lại phiếu rồi thử lại.");
        }
        return borrowingMapper.toResponse(borrowing, LocalDate.now(clock));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelBorrowing(Long borrowingId) {
        Borrowing borrowing = findDetailedBorrowing(borrowingId);
        try {
            borrowing.cancel();
        } catch (IllegalStateException exception) {
            throw new BorrowingException(exception.getMessage());
        }
        for (BorrowingDetail detail : borrowing.getDetails()) {
            detail.getBook().returnCopies(detail.getQuantity());
        }
        try {
            bookRepository.flush();
            borrowingRepository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new BorrowingConflictException("Phiếu hoặc tồn kho vừa thay đổi. Tải lại rồi thử lại.");
        }
    }

    @Transactional(readOnly = true)
    public BorrowingResponseDTO getBorrowing(Long borrowingId) {
        return borrowingMapper.toResponse(findDetailedBorrowing(borrowingId), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<BorrowingResponseDTO> getBorrowings() {
        return borrowingRepository.findAllByOrderByBorrowedAtDesc().stream()
            .map(borrowing -> borrowingMapper.toResponse(borrowing, LocalDate.now(clock)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OverdueBookResponseDTO> getOverdueBorrowings() {
        LocalDate today = LocalDate.now(clock);
        FinePolicy policy = finePolicyRepository.findFirstByActiveTrueOrderByIdAsc()
            .orElseThrow(() -> new BorrowingException("Chưa cấu hình chính sách phí phạt."));
        return detailRepository.findAllOverdue(today).stream()
            .map(detail -> {
                int quantity = detail.getQuantity() - detail.getReturnedQuantity();
                    BigDecimal remainingFineCap = policy.getMaxFineAmount() == null
                        ? null
                        : policy.getMaxFineAmount().subtract(detail.getFineAmount()).max(BigDecimal.ZERO);
                var calculation = fineCalculationService.calculate(
                    detail.getBorrowing().getDueDate(), today, quantity,
                        policy.getDailyFineAmount(), policy.getGraceDays(), remainingFineCap);
                return new OverdueBookResponseDTO(
                    detail.getId(),
                    detail.getBorrowing().getId(),
                    detail.getBorrowing().getMember().getFullName(),
                    detail.getBook().getTitle(),
                    detail.getBorrowing().getDueDate(),
                    Math.toIntExact(ChronoUnit.DAYS.between(
                        detail.getBorrowing().getDueDate(), today)),
                    quantity,
                    calculation.amount());
            })
                .toList();
    }

    private Map<Long, Integer> aggregateItems(List<BorrowingItemDTO> items) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (BorrowingItemDTO item : items) {
            if (item.getBookId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BorrowingException("Sách và số lượng phải hợp lệ.");
            }
            if (quantities.putIfAbsent(item.getBookId(), item.getQuantity()) != null) {
                throw new BorrowingException("Không được chọn trùng một đầu sách trong cùng phiếu.");
            }
        }
        return quantities;
    }

    private Borrowing findDetailedBorrowing(Long borrowingId) {
        return borrowingRepository.findDetailedById(borrowingId)
                .orElseThrow(() -> new BorrowingNotFoundException("Không tìm thấy phiếu mượn."));
    }

    private Map<Long, Integer> selectedQuantities(ReturnFormDTO form) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (form.getReturnedQuantities() != null && !form.getReturnedQuantities().isEmpty()) {
            form.getReturnedQuantities().forEach((id, quantity) -> {
                if (id == null || quantity == null || quantity < 0) {
                    throw new BorrowingException("Số lượng sách trả không hợp lệ.");
                }
                if (quantity > 0) {
                    quantities.put(id, quantity);
                }
            });
        } else if (form.getBorrowingDetailIds() != null) {
            for (Long id : form.getBorrowingDetailIds()) {
                if (id == null || quantities.putIfAbsent(id, 1) != null) {
                    throw new BorrowingException("Danh sách sách trả không hợp lệ.");
                }
            }
        }
        if (quantities.isEmpty()) {
            throw new BorrowingException("Vui lòng nhập số lượng ít nhất một dòng sách để trả.");
        }
        return quantities;
    }

    private List<BorrowingDetail> findSelectedDetails(Long borrowingId, Map<Long, Integer> quantities) {
        List<BorrowingDetail> details = detailRepository.findByIdIn(quantities.keySet());
        if (details.size() != quantities.size()) {
            throw new BorrowingNotFoundException("Không tìm thấy một hoặc nhiều cuốn sách đã chọn.");
        }
        for (BorrowingDetail detail : details) {
            if (!detail.getBorrowing().getId().equals(borrowingId)) {
                throw new BorrowingException("Sách được chọn không thuộc phiếu mượn này.");
            }
            int remainingQuantity = detail.getQuantity() - detail.getReturnedQuantity();
            if (quantities.get(detail.getId()) > remainingQuantity) {
                throw new BorrowingException("Số lượng trả vượt quá số sách còn đang mượn.");
            }
        }
        return details;
    }

    private ReturnPreviewResponseDTO buildReturnPreview(
            Borrowing borrowing,
            List<BorrowingDetail> details,
            Map<Long, Integer> quantities,
            LocalDate returnDate) {
        List<ReturnLineResponseDTO> lines = new ArrayList<>();
        BigDecimal totalFine = BigDecimal.ZERO;
        FinePolicy policy = finePolicyRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new BorrowingException("Chưa cấu hình chính sách phí phạt."));
        for (BorrowingDetail detail : details) {
            int returnedQuantity = quantities.get(detail.getId());
                BigDecimal remainingFineCap = policy.getMaxFineAmount() == null
                    ? null
                    : policy.getMaxFineAmount().subtract(detail.getFineAmount()).max(BigDecimal.ZERO);
            FineCalculationService.FineCalculation calculation = fineCalculationService.calculate(
                    borrowing.getDueDate(),
                    returnDate,
                    returnedQuantity,
                    policy.getDailyFineAmount(),
                    policy.getGraceDays(),
                    remainingFineCap);
            lines.add(new ReturnLineResponseDTO(
                    detail.getId(),
                    detail.getBook().getTitle(),
                    returnedQuantity,
                    borrowing.getDueDate(),
                    calculation.lateDays(),
                    calculation.amount()));
            totalFine = totalFine.add(calculation.amount());
        }
        return new ReturnPreviewResponseDTO(borrowing.getId(), returnDate, lines, totalFine);
    }
}
