package com.library.controller;

import com.library.dto.BorrowingFormDTO;
import com.library.dto.BorrowingItemDTO;
import com.library.dto.OverdueBookResponseDTO;
import com.library.dto.ReturnFormDTO;
import com.library.service.BorrowingException;
import com.library.service.BorrowingNotFoundException;
import com.library.service.BorrowingService;
import com.library.service.FineManagementService;
import com.library.repository.BookRepository;
import com.library.repository.MemberRepository;
import com.library.entity.MemberStatus;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class BorrowingController {

    private final BorrowingService borrowingService;
    private final FineManagementService fineManagementService;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @GetMapping("/")
    public String home() {
        return "redirect:/borrowings";
    }

    @GetMapping("/borrowings")
        public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String member,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Model model) {
        List<com.library.dto.BorrowingResponseDTO> borrowings = borrowingService.getBorrowings().stream()
            .filter(item -> status == null || status.isBlank() || item.status().equals(status))
            .filter(item -> member == null || member.isBlank()
                || item.memberName().toLowerCase(Locale.ROOT).contains(member.toLowerCase(Locale.ROOT)))
            .filter(item -> from == null || !item.borrowedDate().isBefore(from))
            .filter(item -> to == null || !item.borrowedDate().isAfter(to))
            .toList();
        model.addAttribute("borrowings", borrowings);
        model.addAttribute("statusFilter", status);
        model.addAttribute("memberFilter", member);
        model.addAttribute("fromFilter", from);
        model.addAttribute("toFilter", to);
        return "borrowings/list";
    }

    @GetMapping("/borrowings/new")
    public String newBorrowing(Model model) {
        if (!model.containsAttribute("borrowingForm")) {
            BorrowingFormDTO form = new BorrowingFormDTO();
            form.setDueDate(LocalDate.now(clock).plusDays(14));
            form.setItems(List.of(new BorrowingItemDTO()));
            model.addAttribute("borrowingForm", form);
        }
        addBorrowingLookups(model);
        model.addAttribute("defaultDueDate", ((BorrowingFormDTO) model.getAttribute("borrowingForm")).getDueDate());
        return "borrowings/new";
    }

    @PostMapping("/borrowings")
    public String create(
            @Valid @ModelAttribute("borrowingForm") BorrowingFormDTO form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addBorrowingLookups(model);
            model.addAttribute("defaultDueDate", form.getDueDate());
            return "borrowings/new";
        }
        try {
            Long borrowingId = borrowingService.createBorrowing(form).id();
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo phiếu mượn.");
            return "redirect:/borrowings/" + borrowingId;
        } catch (BorrowingException exception) {
            bindingResult.reject("borrowing.error", exception.getMessage());
            addBorrowingLookups(model);
            model.addAttribute("defaultDueDate", form.getDueDate());
            return "borrowings/new";
        }
    }

    @GetMapping("/borrowings/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("borrowing", borrowingService.getBorrowing(id));
        model.addAttribute("payments", fineManagementService.getPayments(id));
        model.addAttribute("waivers", fineManagementService.getWaivers(id));
        if (!model.containsAttribute("returnForm")) {
            model.addAttribute("returnForm", new ReturnFormDTO());
        }
        return "borrowings/detail";
    }

    @GetMapping("/borrowings/{id}/returns/new")
    public String newReturn(@PathVariable Long id, Model model) {
        model.addAttribute("borrowing", borrowingService.getBorrowing(id));
        if (!model.containsAttribute("returnForm")) {
            model.addAttribute("returnForm", new ReturnFormDTO());
        }
        return "borrowings/return";
    }

    @PostMapping("/borrowings/{id}/returns/preview")
    public String previewReturn(
            @PathVariable Long id,
            @Valid @ModelAttribute("returnForm") ReturnFormDTO form,
            BindingResult bindingResult,
            Model model) {
        model.addAttribute("borrowing", borrowingService.getBorrowing(id));
        if (bindingResult.hasErrors()) {
            return "borrowings/return";
        }
        try {
            model.addAttribute("preview", borrowingService.previewReturn(id, form));
            return "borrowings/return-preview";
        } catch (BorrowingException exception) {
            bindingResult.reject("return.error", exception.getMessage());
            return "borrowings/return";
        }
    }

    @PostMapping("/borrowings/{id}/returns")
    public String returnBooks(
            @PathVariable Long id,
            @Valid @ModelAttribute("returnForm") ReturnFormDTO form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("borrowing", borrowingService.getBorrowing(id));
            return "borrowings/return";
        }
        try {
            borrowingService.returnBooks(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận trả sách.");
            return "redirect:/borrowings/" + id;
        } catch (BorrowingException exception) {
            bindingResult.reject("return.error", exception.getMessage());
            model.addAttribute("borrowing", borrowingService.getBorrowing(id));
            return "borrowings/return";
        }
    }

    @PostMapping("/borrowings/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            borrowingService.cancelBorrowing(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy phiếu mượn và hoàn tồn kho.");
        } catch (BorrowingException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/borrowings/" + id;
    }

    @GetMapping("/reports/overdue")
    public String overdue(Model model) {
        List<OverdueBookResponseDTO> overdueBooks = borrowingService.getOverdueBorrowings();
        model.addAttribute("overdueBooks", overdueBooks);
        return "borrowings/overdue";
    }

    @ExceptionHandler(BorrowingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "borrowings/not-found";
    }

    private void addBorrowingLookups(Model model) {
        model.addAttribute("members", memberRepository.findAllByStatusOrderByFullNameAsc(MemberStatus.ACTIVE));
        model.addAttribute("books", bookRepository.findAllByAvailableQuantityGreaterThanOrderByTitleAsc(0));
    }
}
