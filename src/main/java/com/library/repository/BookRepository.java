package com.library.repository;

import com.library.entity.Book;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByAvailableQuantityGreaterThanOrderByTitleAsc(int minimum);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select book from Book book
            where :query is null
               or lower(book.title) like lower(concat('%', :query, '%'))
               or lower(book.author) like lower(concat('%', :query, '%'))
               or lower(book.isbn) like lower(concat('%', :query, '%'))
            """)
    Page<Book> search(@Param("query") String query, Pageable pageable);
}
