package com.jzo2o.orders.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;

import java.time.LocalDateTime;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author itcast
 * @since 2023-07-10
 */
public interface IOrdersCreateService extends IService<Orders> {


    /**
     * 预约下单
     * @param dto
     * @return
     */
    PlaceOrderResDTO placeOrder(PlaceOrderReqDTO dto);

    /**
     * 生成唯一订单ID（订单号）： 年月日+13位自增序列号，长度共计19位
     * @return
     */
    Long generateOrderId();

    /**
     * 保存订单
     * @param orders
     */
    void add(Orders orders);

    /**
     * 保存订单且核销优惠券
     * @param orders
     */
    void addWithCoupon(Orders orders,Long couponId);

    /**
     * 订单支付
     * @param id
     * @param dto
     * @return
     */
    OrdersPayResDTO pay(Long id, OrdersPayReqDTO dto);


    /**
     * 更新订单
     * @param orderId
     * @param nativePayResDTO
     */
    void updateOrders(Long orderId, NativePayResDTO nativePayResDTO);

    /**
     * 查询订单支付结果
     * @param id
     * @return
     */
    OrdersPayResDTO payResult(Long id);


    /**
     * 支付成功更新订单
     * @param orderId
     * @param transactionId
     * @param payTime
     */
    void paySuccess(Long orderId, String transactionId, LocalDateTime payTime);
}
