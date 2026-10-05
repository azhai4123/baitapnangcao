package com.library.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.example.library.LibraryApplication;
import com.library.dto.BorrowingFormDTO;
import com.library.dto.BorrowingItemDTO;
import com.library.entity.Book;
import com.library.entity.Category;
import com.library.entity.FinePolicy;
import com.library.entity.Member;
import com.library.entity.MemberStatus;
import com.library.repository.BookRepository;
import com.library.repository.FinePolicyRepository;
import com.library.repository.MemberRepository;
import com.library.service.BorrowingService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LibraryApplication.class)
@AutoConfigureMockMvc
class BorrowingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private FinePolicyRepository finePolicyRepository;

    @Autowired
    private BorrowingService borrowingService;

    @Test
    void redirectsRootToBorrowings() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/borrowings"));
    }

    @Test
    void servesBootstrapStylesheetFromWebJar() throws Exception {
        mockMvc.perform(get("/webjars/bootstrap/5.3.8/css/bootstrap.min.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bootstrap")));
    }

    @Test
    void rendersBorrowingForm() throws Exception {
        mockMvc.perform(get("/borrowings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/new"))
                .andExpect(content().string(containsString("Lập phiếu mượn")))
                .andExpect(content().string(containsString("form-select")))
                .andExpect(content().string(containsString("/webjars/bootstrap/5.3.8/css/bootstrap.min.css")));
    }

    @Test
    void rendersBorrowingListWithBootstrapNavigationAndTable() throws Exception {
        mockMvc.perform(get("/borrowings"))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/list"))
                .andExpect(content().string(containsString("navbar-expand-lg")))
                .andExpect(content().string(containsString("table-responsive")));
    }

            @Test
            @Transactional
            void rendersBorrowingDetailReturnPaymentAndWaiverPages() throws Exception {
                finePolicyRepository.save(new FinePolicy(BigDecimal.valueOf(5_000), null, 0, true));
            Member member = memberRepository.save(
                new Member("Bootstrap View Test", "bootstrap-view@example.test", MemberStatus.ACTIVE));
            Category category = new Category("Bootstrap View Category", null);
            entityManager.persist(category);
            Book book = bookRepository.save(new Book(
                category, "9780000000099", "Bootstrap View Book", "Test Author", null, null, 2, 2));

            BorrowingFormDTO form = new BorrowingFormDTO();
            form.setMemberId(member.getId());
            form.setDueDate(java.time.LocalDate.now().plusDays(7));
            BorrowingItemDTO item = new BorrowingItemDTO();
            item.setBookId(book.getId());
            item.setQuantity(1);
            form.setItems(java.util.List.of(item));
            Long borrowingId = borrowingService.createBorrowing(form).id();

            mockMvc.perform(get("/borrowings/" + borrowingId))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/detail"))
                .andExpect(content().string(containsString("Tổng quan phí phạt")));
            mockMvc.perform(get("/borrowings/" + borrowingId + "/returns/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/return"));
            mockMvc.perform(get("/borrowings/" + borrowingId + "/payments/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("fines/payment"));
            mockMvc.perform(get("/borrowings/" + borrowingId + "/waivers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("fines/waiver"));
            Long detailId = borrowingService.getBorrowing(borrowingId).items().get(0).id();
            mockMvc.perform(post("/borrowings/" + borrowingId + "/returns/preview")
                    .param("returnedQuantities[" + detailId + "]", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/return-preview"));
            mockMvc.perform(get("/reports/overdue"))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/overdue"));
            }

    @Test
    void rendersBookSearchPage() throws Exception {
        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(view().name("books/list"))
                .andExpect(content().string(containsString("Danh sách sách")));
    }

    @Test
    void rendersFinePolicyPageWithoutExistingPolicy() throws Exception {
        mockMvc.perform(get("/fine-policies"))
                .andExpect(status().isOk())
                .andExpect(view().name("fines/policies"))
                .andExpect(content().string(containsString("Chính sách phí phạt")));
    }

    @Test
    void returnsFieldValidationErrorsWithoutCreatingBorrowing() throws Exception {
        mockMvc.perform(post("/borrowings")
                        .param("items[0].bookId", "")
                        .param("items[0].quantity", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("borrowings/new"))
                .andExpect(content().string(containsString("Vui lòng chọn thành viên.")))
                .andExpect(content().string(containsString("Số lượng phải lớn hơn 0.")));
    }
}
