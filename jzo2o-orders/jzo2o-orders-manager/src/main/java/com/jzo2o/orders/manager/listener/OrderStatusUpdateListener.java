package com.jzo2o.orders.manager.listener;

import com.alibaba.fastjson.JSON;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.constants.MqConstants;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单状态更新的MQ消费者监听器
 *
 * @Author mr.wu
 * @Date 2024-12-10 16:15
 */
@Slf4j
@Component
public class OrderStatusUpdateListener {


    @Autowired
    private TradeProperties tradeProperties;

    @Autowired
    private IOrdersCreateService ordersCreateService;


    /**
     * 更新支付成功状态
     * @param msg
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(name = MqConstants.Queues.ORDERS_TRADE_UPDATE_STATUS),
            exchange = @Exchange(name = MqConstants.Exchanges.TRADE, type = ExchangeTypes.TOPIC),
            key = MqConstants.RoutingKeys.TRADE_UPDATE_STATUS
    ))
    public void listenMsgForPaySuccess(String msg) {

        //1. 将消息JSON字符串转为消息对象列表
        List<TradeStatusMsg> tradeStatusMsgList = JSON.parseArray(msg, TradeStatusMsg.class);
        if (CollUtils.isEmpty(tradeStatusMsgList)) {
            return;
        }

        //2. 遍历消息对象列表筛选归属于订单系统的数据并执行更新订单操作
        tradeStatusMsgList.stream()
                .filter(x -> x.getProductAppId().equals(tradeProperties.getProductAppId())
                        && x.getStatusCode().equals(TradingStateEnum.YJS.getCode()))
                .forEach(y -> ordersCreateService.paySuccess(y.getProductOrderNo(), y.getTransactionId(), y.getPaySuccessTime()));


        log.info("[订单状态变更]支付成功状态变更完毕>>>>>>");
    }

    @Autowired
    private IOrdersManagerService ordersManagerService;

    /**
     * 超时未支付订单自动取消
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(name = MqConstants.Queues.ORDERS_CANCEL_QUEUE),
            exchange = @Exchange(name = MqConstants.Exchanges.ORDERS, delayed = "true"),
            key = MqConstants.RoutingKeys.ORDERS_CANCEL_KEY
    ))
    public void listenMsgForCancelNoPay(String orderIdStr) {
        //1.判断订单ID是否为空
        if(ObjectUtils.isNull(orderIdStr)) {
            return;
        }

        //2.判断订单是否为空
        Long orderId = Long.valueOf(orderIdStr);
        Orders orders = ordersCreateService.getById(orderId);
        if(ObjectUtils.isNull(orders)) {
            return;
        }

        //3.判断订单状态是否未支付
        if(ObjectUtils.notEqual(orders.getOrdersStatus(), OrderStatusEnum.NO_PAY.getStatus())) {
            return;
        }

        //4.添加取消记录及更新订单表状态
        ordersManagerService.cancelOrderByNoPay(orderId, "超时自动取消", true);

        log.info("[超时未支付自动取消]自动取消完毕，订单id：{}", orderId);
    }
}
