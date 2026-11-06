package com.jzo2o.orders.base.statemachine;

import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.service.IHistoryOrdersSyncCommonService;
import com.jzo2o.statemachine.AbstractStateMachine;
import com.jzo2o.statemachine.core.StatusDefine;
import com.jzo2o.statemachine.persist.StateMachinePersister;
import com.jzo2o.statemachine.snapshot.BizSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单状态机类
 *
 * @Author mr.wu
 * @Date 2024-12-17 9:44
 */
@Component
public class OrderStateMachine extends AbstractStateMachine<OrderSnapshotDTO> {


    protected OrderStateMachine(StateMachinePersister stateMachinePersister, BizSnapshotService bizSnapshotService, RedisTemplate redisTemplate) {
        super(stateMachinePersister, bizSnapshotService, redisTemplate);
    }

    /**
     * 状态机名称
     * @return
     */
    @Override
    protected String getName() {
        return "order";
    }

    @Autowired
    private IHistoryOrdersSyncCommonService historyOrdersSyncCommonService;


    @Override
    protected void postProcessor(OrderSnapshotDTO orderSnapshotDTO) {
        /***************************完成、关闭、取消订单写历史订单同步表*******************************/
        //取出订单的新状态
        Integer ordersStatus = orderSnapshotDTO.getOrdersStatus();
        if(OrderStatusEnum.FINISHED.getStatus().equals(ordersStatus) ||
                OrderStatusEnum.CLOSED.getStatus().equals(ordersStatus) ||
                OrderStatusEnum.CANCELED.getStatus().equals(ordersStatus) ){
            historyOrdersSyncCommonService.writeHistorySync(orderSnapshotDTO.getId());
        }
    }

    @Override
    protected StatusDefine getInitState() {
        return OrderStatusEnum.NO_PAY;
    }
}
