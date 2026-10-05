package com.library.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FinePaymentFormDTO {

    @NotNull(message = "Vui lòng nhập số tiền thanh toán.")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0.")
    private BigDecimal amount;

    @NotBlank(message = "Vui lòng chọn phương thức thanh toán.")
    private String method;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự.")
    private String note;
}