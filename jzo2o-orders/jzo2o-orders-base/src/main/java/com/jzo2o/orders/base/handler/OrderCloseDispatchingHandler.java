package com.jzo2o.orders.base.handler;

import com.jzo2o.common.expcetions.DBException;
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
 * 派单中订单关闭事件触发的动作处理类
 *
 * @Author mr.wu
 * @Date 2024-12-17 9:48
 */
@Slf4j
@Component("order_close_dispatching_order") //动作处理类的Bean名称命名规则：状态机名_事件名
public class OrderCloseDispatchingHandler implements StatusChangeHandler<OrderSnapshotDTO> {

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
        log.info("派单中订单关闭事件，触发状态变更，开始变更：{}", bizId);
        //更新订单表的退款相关字段的值 update orders set refund_status=?,refund_no=?,refund_id=? where id=?
        boolean resultForOrder = ordersCommonService.lambdaUpdate()
                .eq(Orders::getId, bizSnapshot.getId())//订单id
                .set(Orders::getOrdersStatus, OrderStatusEnum.CLOSED.getStatus()) //订单状态：已关闭
                .set(Orders::getRefundStatus, bizSnapshot.getRefundStatus()) //退款状态
                .set(Orders::getRefundNo, bizSnapshot.getRefundNo())//支付服务退款订单号
                .set(Orders::getRefundId, bizSnapshot.getThirdRefundOrderId())//第三方退款订单号
                .update();
        if(!resultForOrder) {
            throw new DBException("[退款操作]更新订单表失败");
        }

        log.info("派单中订单关闭事件，触发状态变更，变更完毕：{}", bizId);
    }
}
