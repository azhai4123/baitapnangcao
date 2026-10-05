package com.library.service;

import com.library.dto.FinePolicyFormDTO;
import com.library.entity.Borrowing;
import com.library.entity.FinePayment;
import com.library.entity.FinePolicy;
import com.library.entity.FineWaiver;
import com.library.repository.BorrowingRepository;
import com.library.repository.FinePaymentRepository;
import com.library.repository.FinePolicyRepository;
import com.library.repository.FineWaiverRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FineManagementService {

    private final BorrowingRepository borrowingRepository;
    private final FinePaymentRepository paymentRepository;
    private final FinePolicyRepository policyRepository;
    private final FineWaiverRepository waiverRepository;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public void recordPayment(Long borrowingId, BigDecimal amount, String method, String note) {
        Borrowing borrowing = lockBorrowing(borrowingId);
        try {
            borrowing.recordPayment(amount);
        } catch (IllegalArgumentException exception) {
            throw new BorrowingException(exception.getMessage());
        }
        paymentRepository.save(new FinePayment(
                borrowing, amount, LocalDateTime.now(clock), method.trim(), note));
        borrowingRepository.flush();
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordWaiver(Long borrowingId, BigDecimal amount, String reason, String approvedBy) {
        Borrowing borrowing = lockBorrowing(borrowingId);
        try {
            borrowing.recordWaiver(amount);
        } catch (IllegalArgumentException exception) {
            throw new BorrowingException(exception.getMessage());
        }
        waiverRepository.save(new FineWaiver(
                borrowing, amount, reason.trim(), approvedBy.trim(), LocalDateTime.now(clock)));
        borrowingRepository.flush();
    }

    @Transactional
    public FinePolicy activatePolicy(FinePolicyFormDTO form) {
        List<FinePolicy> activePolicies = policyRepository.lockAllActive();
        activePolicies.forEach(FinePolicy::deactivate);
        policyRepository.flush();
        FinePolicy policy = new FinePolicy(
                form.getDailyFineAmount(), form.getMaxFineAmount(), form.getGraceDays(), true);
        return policyRepository.saveAndFlush(policy);
    }

    @Transactional(readOnly = true)
    public List<FinePolicy> getPolicies() {
        return policyRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public FinePolicy getActivePolicy() {
        return policyRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new BorrowingException("Chưa cấu hình chính sách phí phạt."));
    }

    @Transactional(readOnly = true)
    public List<FinePayment> getPayments(Long borrowingId) {
        if (!borrowingRepository.existsById(borrowingId)) {
            throw new BorrowingNotFoundException("Không tìm thấy phiếu mượn.");
        }
        return paymentRepository.findAllByBorrowingIdOrderByPaymentDateDesc(borrowingId);
    }

    @Transactional(readOnly = true)
    public List<FineWaiver> getWaivers(Long borrowingId) {
        if (!borrowingRepository.existsById(borrowingId)) {
            throw new BorrowingNotFoundException("Không tìm thấy phiếu mượn.");
        }
        return waiverRepository.findAllByBorrowingIdOrderByApprovedDateDesc(borrowingId);
    }

    private Borrowing lockBorrowing(Long borrowingId) {
        return borrowingRepository.lockById(borrowingId)
                .orElseThrow(() -> new BorrowingNotFoundException("Không tìm thấy phiếu mượn."));
    }
}