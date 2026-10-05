package com.library.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReturnFormDTO {

    private List<@NotNull(message = "Mã chi tiết không hợp lệ.") Long> borrowingDetailIds;

    private Map<Long, Integer> returnedQuantities;
}
