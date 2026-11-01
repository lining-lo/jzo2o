package com.jzo2o.orders.manager.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.model.dto.request.OrderPageQueryReqDTO;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OperationOrdersDetailResDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 */
public interface IOrdersCreateService extends IService<Orders> {

    /**
     * 下单服务
     * @param placeOrderReqDTO
     * @return
     */
    PlaceOrderResDTO placeOrder(PlaceOrderReqDTO placeOrderReqDTO);

    /**
     * 生成唯一订单号（yyMMdd+13位自增序号）
     *
     * @return
     */
    public Long generateOrderId();

    void add(Orders orders);

    /**
     * 订单支付
     *
     * @param id              订单id
     * @param ordersPayReqDTO 订单支付请求体
     * @return 订单支付响应体
     */
    OrdersPayResDTO pay(Long id, OrdersPayReqDTO ordersPayReqDTO);


    /**
     * 更新订单
     * @param id
     * @param nativePayResDTO
     */
    void updateOrders(Long id, NativePayResDTO nativePayResDTO);

    /**
     * 请求支付服务查询支付结果
     *
     * @param id 订单id
     * @return 订单支付结果
     */
    OrdersPayResDTO getPayResultFromTradServer(Long id);

    /**
     * 支付成功更新订单
     * @param orderId
     * @param transactionId
     * @param payTime
     */
    void paySuccess(Long orderId, String transactionId, LocalDateTime payTime);
}
