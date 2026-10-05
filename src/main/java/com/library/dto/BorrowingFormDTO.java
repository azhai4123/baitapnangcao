package com.library.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BorrowingFormDTO {

    @NotNull(message = "Vui lòng chọn thành viên.")
    private Long memberId;

    @NotNull(message = "Vui lòng chọn hạn trả.")
    @FutureOrPresent(message = "Hạn trả không được ở trong quá khứ.")
    private LocalDate dueDate;

    private String notes;

    @NotEmpty(message = "Phiếu mượn phải có ít nhất một cuốn sách.")
    @Valid
    private List<@Valid BorrowingItemDTO> items = new ArrayList<>();
}
