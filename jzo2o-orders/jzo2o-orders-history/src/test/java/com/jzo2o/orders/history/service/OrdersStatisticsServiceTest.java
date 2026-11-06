package com.jzo2o.orders.history.service;

import com.jzo2o.orders.history.model.dto.response.OperationHomePageResDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;

@SpringBootTest
class OrdersStatisticsServiceTest {
    @Resource
    private OrdersStatisticsService ordersStatisticsService;


    @Test
    void test(){
        OperationHomePageResDTO operationHomePageResDTO = ordersStatisticsService.homePage(LocalDateTime.now().plusDays(-2), LocalDateTime.now().plusDays(2));
        System.out.println(operationHomePageResDTO);
    }

    @Test
    void test01() throws IOException {
        ordersStatisticsService.downloadStatistics(LocalDateTime.now().plusDays(-2), LocalDateTime.now().plusDays(2));

    }
}