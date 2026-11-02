package com.jzo2o.orders.manager.job;

import com.jzo2o.api.trade.RefundRecordApi;
import com.jzo2o.api.trade.dto.response.ExecutionResultResDTO;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单管理微服务的定时任务
 */
@Slf4j
@Component
public class OrderJob {

    @Autowired
    private IOrdersRefundService ordersRefundService;
    
    @Autowired
    private IOrdersManagerService ordersManagerService;;

    /**
     * 定时自动退款
     */
    @XxlJob("handleRefundOrders")
    public void batchOrderRefund(){
        //1.查询退款记录列表（每次查100条）
        List<OrdersRefund> ordersRefundList = ordersRefundService.lambdaQuery().last("limit 100").list();
        if(CollUtils.isEmpty(ordersRefundList)) {
            return;
        }
        
        //2.遍历退款记录列表，逐一进行退款处理
        for (OrdersRefund ordersRefund : ordersRefundList) {
            ordersManagerService.ordersRefundHandle(ordersRefund);
        }
    }
}