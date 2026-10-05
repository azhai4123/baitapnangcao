package com.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(length = 20, unique = true)
    private String isbn;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String author;

    @Column(length = 255)
    private String publisher;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version
    @Column(nullable = false)
    private int version;

    public Book(
            Category category,
            String isbn,
            String title,
            String author,
            String publisher,
            Integer publicationYear,
            int totalQuantity,
            int availableQuantity) {
        this.category = category;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
    }

    public void borrowCopies(int quantity) {
        if (quantity < 1 || quantity > availableQuantity) {
            throw new IllegalArgumentException("Số lượng mượn vượt quá tồn kho khả dụng.");
        }
        availableQuantity -= quantity;
    }

    public void returnCopy() {
        if (availableQuantity >= totalQuantity) {
            throw new IllegalStateException("Tồn kho khả dụng đã bằng tổng số bản sách.");
        }
        availableQuantity++;
    }

    public void returnCopies(int quantity) {
        if (quantity < 1 || availableQuantity + quantity > totalQuantity) {
            throw new IllegalStateException("Số lượng trả không hợp lệ với tồn kho.");
        }
        availableQuantity += quantity;
    }
}
