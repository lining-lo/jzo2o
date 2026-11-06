package com.jzo2o.orders.manager.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 订单相关业务方法测试
 *
 * @Author mr.wu
 * @Date 2024-12-9 14:42
 */

@SpringBootTest
public class OrderCrateServiceTest {

    @Autowired
    private IOrdersCreateService ordersCreateService;

    @Test
    public void testGenerateOrderId(){
        for (int i = 0; i < 10; i++) {
            Long orderId = ordersCreateService.generateOrderId();
            System.out.println(orderId);
        }
    }

}
