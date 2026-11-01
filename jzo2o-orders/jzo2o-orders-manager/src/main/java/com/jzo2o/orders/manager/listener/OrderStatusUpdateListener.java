package com.jzo2o.orders.manager.listener;

import com.alibaba.fastjson.JSON;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.constants.MqConstants;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class OrderStatusUpdateListener {

    @Autowired
    private TradeProperties tradeProperties;

    @Autowired
    private IOrdersCreateService ordersCreateService;


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

}