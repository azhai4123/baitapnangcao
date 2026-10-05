package com.library.controller;

import com.library.dto.FinePaymentFormDTO;
import com.library.dto.FinePolicyFormDTO;
import com.library.dto.FineWaiverFormDTO;
import com.library.service.BorrowingException;
import com.library.service.BorrowingService;
import com.library.service.FineManagementService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class FineController {

    private final FineManagementService fineManagementService;
    private final BorrowingService borrowingService;

    @GetMapping("/fine-policies")
    public String policies(Model model) {
        model.addAttribute("policies", fineManagementService.getPolicies());
        if (!model.containsAttribute("policyForm")) {
            FinePolicyFormDTO form = new FinePolicyFormDTO();
            try {
                var active = fineManagementService.getActivePolicy();
                form.setDailyFineAmount(active.getDailyFineAmount());
                form.setMaxFineAmount(active.getMaxFineAmount());
                form.setGraceDays(active.getGraceDays());
            } catch (BorrowingException ignored) {
                form.setDailyFineAmount(BigDecimal.valueOf(5_000));
            }
            model.addAttribute("policyForm", form);
        }
        return "fines/policies";
    }

    @PostMapping("/fine-policies")
    public String activatePolicy(
            @Valid @ModelAttribute("policyForm") FinePolicyFormDTO form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (form.getMaxFineAmount() != null && form.getMaxFineAmount().signum() <= 0) {
            bindingResult.rejectValue("maxFineAmount", "policy.max", "Mức trần phải lớn hơn 0 hoặc để trống.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("policies", fineManagementService.getPolicies());
            return "fines/policies";
        }
        fineManagementService.activatePolicy(form);
        redirectAttributes.addFlashAttribute("successMessage", "Đã áp dụng chính sách phí phạt mới.");
        return "redirect:/fine-policies";
    }

    @GetMapping("/borrowings/{id}/payments/new")
    public String newPayment(@PathVariable Long id, Model model) {
        model.addAttribute("borrowing", borrowingService.getBorrowing(id));
        if (!model.containsAttribute("paymentForm")) {
            model.addAttribute("paymentForm", new FinePaymentFormDTO());
        }
        return "fines/payment";
    }

    @PostMapping("/borrowings/{id}/payments")
    public String recordPayment(
            @PathVariable Long id,
            @Valid @ModelAttribute("paymentForm") FinePaymentFormDTO form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("borrowing", borrowingService.getBorrowing(id));
            return "fines/payment";
        }
        try {
            fineManagementService.recordPayment(id, form.getAmount(), form.getMethod(), form.getNote());
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận thanh toán phí phạt.");
            return "redirect:/borrowings/" + id;
        } catch (BorrowingException exception) {
            bindingResult.reject("payment.error", exception.getMessage());
            model.addAttribute("borrowing", borrowingService.getBorrowing(id));
            return "fines/payment";
        }
    }

    @GetMapping("/borrowings/{id}/waivers/new")
    public String newWaiver(@PathVariable Long id, Model model) {
        model.addAttribute("borrowing", borrowingService.getBorrowing(id));
        if (!model.containsAttribute("waiverForm")) {
            model.addAttribute("waiverForm", new FineWaiverFormDTO());
        }
        return "fines/waiver";
    }

    @PostMapping("/borrowings/{id}/waivers")
    public String recordWaiver(
            @PathVariable Long id,
            @Valid @ModelAttribute("waiverForm") FineWaiverFormDTO form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("borrowing", borrowingService.getBorrowing(id));
            return "fines/waiver";
        }
        try {
            fineManagementService.recordWaiver(
                    id, form.getAmount(), form.getReason(), form.getApprovedBy());
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận miễn giảm phí phạt.");
            return "redirect:/borrowings/" + id;
        } catch (BorrowingException exception) {
            bindingResult.reject("waiver.error", exception.getMessage());
            model.addAttribute("borrowing", borrowingService.getBorrowing(id));
            return "fines/waiver";
        }
    }

    @ExceptionHandler(com.library.service.BorrowingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "borrowings/not-found";
    }
}