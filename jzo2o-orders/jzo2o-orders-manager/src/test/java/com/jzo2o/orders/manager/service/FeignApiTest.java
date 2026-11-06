package com.jzo2o.orders.manager.service;

import com.jzo2o.api.customer.AddressBookApi;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Feign接口测试类
 *
 * @Author mr.wu
 * @Date 2024-12-9 10:30
 */
@SpringBootTest
public class FeignApiTest {

    @Autowired
    private AddressBookApi addressBookApi;

    @Autowired
    private ServeApi serveApi;

    @Test
    public void testFindAddressById(){
        AddressBookResDTO address = addressBookApi.findById(1863852982225416194L);
        System.out.println(address);
    }

    @Test
    public void testFindServeById(){
        ServeAggregationResDTO aggregationResDTO = serveApi.findById(1864585227423281153L);
        System.out.println(aggregationResDTO);
    }
}
