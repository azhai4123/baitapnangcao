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
public class FineWaiverFormDTO {

    @NotNull(message = "Vui lòng nhập số tiền miễn giảm.")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0.")
    private BigDecimal amount;

    @NotBlank(message = "Lý do miễn giảm là bắt buộc.")
    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự.")
    private String reason;

    @NotBlank(message = "Vui lòng nhập người phê duyệt.")
    @Size(max = 200, message = "Tên người phê duyệt không được vượt quá 200 ký tự.")
    private String approvedBy;
}