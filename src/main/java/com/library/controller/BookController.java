package com.library.controller;

import com.library.dto.BookListItemDTO;
import com.library.repository.BookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class BookController {

    private final BookRepository bookRepository;

    @GetMapping("/books")
    public String books(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        int pageNumber = Math.max(0, page);
        var bookPage = bookRepository.search(
                q.isBlank() ? null : q.trim(),
                PageRequest.of(pageNumber, 20, Sort.by("title").ascending()));
        List<BookListItemDTO> books = bookPage.getContent().stream()
                .map(book -> new BookListItemDTO(
                        book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                        book.getCategory().getName(), book.getTotalQuantity(), book.getAvailableQuantity()))
                .toList();
        model.addAttribute("books", books);
        model.addAttribute("bookPage", bookPage);
        model.addAttribute("query", q);
        return "books/list";
    }
}