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
 * 订单支付成功事件触发的动作处理类
 *
 * @Author mr.wu
 * @Date 2024-12-17 9:48
 */
@Slf4j
@Component("order_payed") //动作处理类的Bean名称命名规则：状态机名_事件名
public class OrderPayedHandler implements StatusChangeHandler<OrderSnapshotDTO> {

    @Autowired
    private IOrdersCommonService ordersCommonService;


    /**
     * 状态变更处理逻辑
     * @param bizId
     * @param statusChangeEventEnum
     * @param bizSnapshot
     */
    @Override
    public void handler(String bizId, StatusChangeEvent statusChangeEventEnum, OrderSnapshotDTO bizSnapshot) {
        log.info("支付成功事件，触发状态变更，开始变更：{}", bizId);
        boolean result = ordersCommonService.lambdaUpdate()
                .eq(Orders::getId, bizSnapshot.getId())
                .set(Orders::getPayTime, bizSnapshot.getPayTime())
                .set(Orders::getTransactionId, bizSnapshot.getThirdOrderId())
                .set(Orders::getOrdersStatus, OrderStatusEnum.DISPATCHING.getStatus())
                .set(Orders::getPayStatus, OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                .update();
        if(!result){
            throw new DBException("更新订单失败");
        }
        log.info("支付成功事件，触发状态变更，开始变更：{}", bizId);
    }
}
