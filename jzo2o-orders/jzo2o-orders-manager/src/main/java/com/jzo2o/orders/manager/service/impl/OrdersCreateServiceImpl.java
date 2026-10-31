package com.jzo2o.orders.manager.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 下单服务类
 * </p>
 */
@Slf4j
@Service
public class OrdersCreateServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersCreateService {

    @Override
    public PlaceOrderResDTO placeOrder(PlaceOrderReqDTO placeOrderReqDTO) {

        //1. 调用feign接口（foundations服务的）查询服务信息

        //2. 调用feign接口（customer服务的）查询地址信息

        //3. 封装订单对象信息

        //3.1 订单基本信息封装

        //3.2 下单人信息封装

        //3.3  支付相关信息（暂时不处理）

        //3.4 服务信息封装

        //3.5 价格信息封装

        //4. 创建订单到订单表

        return null;
    }
}