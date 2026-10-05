package com.library.dto;

public record BookListItemDTO(
        Long id,
        String title,
        String author,
        String isbn,
        String categoryName,
        int totalQuantity,
        int availableQuantity) {
}