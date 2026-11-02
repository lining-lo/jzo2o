package com.jzo2o.orders.base.handler;

import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.statemachine.core.StatusChangeEvent;
import com.jzo2o.statemachine.core.StatusChangeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单状态变更事件动作处理类
 */
@Slf4j
@Component("order_payed")
public class OrderPayedHandler implements StatusChangeHandler<OrderSnapshotDTO> {

    @Autowired
    private IOrdersCommonService ordersCommonService;

    /**
     * 支付成功状态变更
     * @param bizId 订单id
     * @param statusChangeEventEnum 订单状态变更事件枚举类
     * @param bizSnapshot 订单快照
     */
    @Override
    public void handler(String bizId, StatusChangeEvent statusChangeEventEnum, OrderSnapshotDTO bizSnapshot) {
        log.info("订单支付成功，订单状态开始变更，订单ID：{}", bizId);

        boolean result = ordersCommonService.lambdaUpdate()
                .eq(Orders::getId, Long.valueOf(bizId))
                .set(Orders::getPayTime, bizSnapshot.getPayTime())
                .set(Orders::getTransactionId, bizSnapshot.getTransactionId())
                .set(Orders::getOrdersStatus, OrderStatusEnum.DISPATCHING.getStatus())
                .set(Orders::getPayStatus, OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                .update();
        if(!result){
            throw new DBException("更新订单失败");
        }


        log.info("订单支付成功，订单状态变更完毕，订单ID：{}", bizId);
    }
}