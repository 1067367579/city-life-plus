package com.hmdp.rabbit;

import com.hmdp.constants.RabbitConstants;
import com.hmdp.domain.dto.CreateOrderDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void produceOrder(CreateOrderDTO createOrderDTO) {
        rabbitTemplate.convertAndSend(RabbitConstants.SECKILL_QUEUE_NAME,createOrderDTO);
    }
}
