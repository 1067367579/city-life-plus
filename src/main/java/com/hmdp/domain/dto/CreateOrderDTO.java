package com.hmdp.domain.dto;

import lombok.Data;

@Data
public class CreateOrderDTO {
    private Long userId;
    private Long voucherId;
    private Long orderId;
}
