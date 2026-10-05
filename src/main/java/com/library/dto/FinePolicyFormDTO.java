package com.library.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FinePolicyFormDTO {

    @NotNull(message = "Vui lòng nhập mức phí mỗi ngày.")
    @DecimalMin(value = "0.00", message = "Mức phí không được âm.")
    private BigDecimal dailyFineAmount;

    @DecimalMin(value = "0.00", message = "Mức trần không được âm.")
    private BigDecimal maxFineAmount;

    @Min(value = 0, message = "Số ngày miễn phạt không được âm.")
    private int graceDays;
}