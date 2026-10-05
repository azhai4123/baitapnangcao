package com.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BorrowingItemDTO {

    @NotNull(message = "Vui lòng chọn sách.")
    private Long bookId;

    @NotNull(message = "Vui lòng nhập số lượng.")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0.")
    private Integer quantity;
}
