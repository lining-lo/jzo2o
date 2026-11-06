package com.jzo2o.orders.manager.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.jzo2o.api.customer.EvaluationApi;
import com.jzo2o.api.customer.dto.request.EvaluationSubmitReqDTO;
import com.jzo2o.api.orders.dto.request.OrderCancelReqDTO;
import com.jzo2o.api.trade.RefundRecordApi;
import com.jzo2o.api.trade.dto.response.ExecutionResultResDTO;
import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.model.CurrentUserInfo;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.statemachine.OrderStateMachine;
import com.jzo2o.orders.manager.porperties.OrdersJobProperties;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单微服务的定时任务类
 *
 * @Author mr.wu
 * @Date 2024-12-13 11:31
 */
@Slf4j
@Component
public class OrdersJob {

    @Autowired
    private IOrdersRefundService ordersRefundService;

    @Autowired
    private RefundRecordApi refundRecordApi;

    @Autowired
    private IOrdersManagerService ordersManagerService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OrderStateMachine orderStateMachine;
    @Autowired
    private OrdersJobProperties ordersJobProperties;
    @Autowired
    private EvaluationApi evaluationApi;


    /**
     * 定时任务自动执行退款操作
     */
    @XxlJob("handleRefundOrders")
    public void refundOrder(){
        //1.分批查询待退款记录列表
        List<OrdersRefund> ordersRefundList = ordersRefundService.lambdaQuery().last(" limit 100").list();

        //2.判断结果集是否为空
        if(CollUtils.isEmpty(ordersRefundList)) {
            return;
        }

        //3.遍历并逐一执行退款操作
        for (OrdersRefund ordersRefund : ordersRefundList) {
            //获取@Component类的代理对象，这样调用事务方法事务才能生效
            OrdersJob owner = applicationContext.getBean(OrdersJob.class);
            owner.refundOrderRequest(ordersRefund);
        }

    }

    @Transactional(rollbackFor = Exception.class)
    public void refundOrderRequest(OrdersRefund ordersRefund) {
        //3.1 调用支付服务的Feign接口发起退款申请
        Integer refundStatus =  OrderRefundStatusEnum.REFUNDING.getStatus();
        ExecutionResultResDTO executionResultResDTO = null;
        try {
            executionResultResDTO = refundRecordApi.refundTrading(ordersRefund.getTradingOrderNo(), ordersRefund.getRealPayAmount());
            refundStatus = executionResultResDTO.getRefundStatus();

            //如果退款申请结束后，依然是退款中状态，则隔3秒后再发一次退款申请就是查询操作
            if(ObjectUtils.equals(refundStatus, OrderRefundStatusEnum.REFUNDING.getStatus())) {
                TimeUnit.SECONDS.sleep(3);
                executionResultResDTO = refundRecordApi.refundTrading(ordersRefund.getTradingOrderNo(), ordersRefund.getRealPayAmount());
                refundStatus = executionResultResDTO.getRefundStatus();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BadRequestException("[退款操作]调用支付服务退款接口失败，待退款记录信息："+ ordersRefund);
        }
        if(executionResultResDTO==null) {
            throw new BadRequestException("[退款操作]调用支付服务退款接口失败，待退款记录信息："+ ordersRefund);
        }
        //3.2 触发派单中订单关闭事件，进行状态变更
        OrderSnapshotDTO orderSnapshotDTO = new OrderSnapshotDTO();
        orderSnapshotDTO.setId(ordersRefund.getId());//订单id
        orderSnapshotDTO.setRefundStatus(refundStatus);//退款状态
        orderSnapshotDTO.setRefundNo(executionResultResDTO.getRefundNo());//支付服务退款订单号
        orderSnapshotDTO.setThirdRefundOrderId(executionResultResDTO.getRefundId());//第三方退款订单号
        Orders orders = ordersManagerService.getById(ordersRefund.getId());
        orderStateMachine.changeStatus(orders.getUserId(),ordersRefund.getId().toString(), OrderStatusChangeEventEnum.CLOSE_DISPATCHING_ORDER, orderSnapshotDTO);

        //3.3 删除待退款记录
        boolean resultForRefund = ordersRefundService.removeById(ordersRefund.getId());
        if(!resultForRefund) {
            throw new DBException("[退款操作]删除待退款记录失败，待退款记录信息："+ ordersRefund);
        }

        log.info("[退款处理]退款申请及结果处理成功，待退款记录信息：{}，最终退款状态：{}",ordersRefund , refundStatus);
    }


    @Autowired
    private IOrdersCanceledService ordersCanceledService;



    /**
     * 取消待派单订单
     *
     * @param orders      订单
     * @param cancelReason 取消原因
     */
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByDispatching(Orders orders, String cancelReason, CurrentUserInfo currentUserInfo) {
        //1. 添加取消记录
        OrdersCanceled ordersCanceled = new OrdersCanceled();
        ordersCanceled.setId(orders.getId());//订单id
        ordersCanceled.setCancellerId(currentUserInfo.getId());//取消人id
        ordersCanceled.setCancellerType(currentUserInfo.getUserType());//取消人类型
        ordersCanceled.setCancelerName(currentUserInfo.getName());//取消人姓名
        ordersCanceled.setCancelReason(cancelReason);//取消原因
        ordersCanceled.setCancelTime(LocalDateTime.now());//取消时间
        boolean resultForCancel = ordersCanceledService.save(ordersCanceled);
        if(!resultForCancel) {
            throw new DBException("添加取消记录失败");
        }

        //2. 更新订单状态 update orders set orders_status=700,refund_status=1 where id=?
//        boolean resultForOrders = ordersManagerService.lambdaUpdate()
//                .eq(Orders::getId, orders.getId())
//                .set(Orders::getOrdersStatus, OrderStatusEnum.CLOSED.getStatus())
//                .set(Orders::getRefundStatus, RefundStatusEnum.SENDING.getCode())
//                .update();
//        if(!resultForOrders) {
//            throw new DBException("更新订单失败");
//        }

        //2. 添加待退款记录
        OrdersRefund ordersRefund = new OrdersRefund();
        ordersRefund.setId(orders.getId());//订单id
        ordersRefund.setTradingOrderNo(orders.getTradingOrderNo());//支付服务支付订单号
        //ordersRefund.setRealPayAmount(orders.getRealPayAmount());
        ordersRefund.setRealPayAmount(BigDecimal.valueOf(0.01));//写成0.01方便测试
        boolean resultForRefund = ordersRefundService.save(ordersRefund);
        if(!resultForRefund) {
            throw new DBException("添加退款记录失败");
        }

        //获取@Component类的代理对象，这样调用事务方法事务才能生效
        OrdersJob owner = applicationContext.getBean(OrdersJob.class);

        // 4.发起退款申请及后续操作
        owner.refundOrderRequest(ordersRefund);
    }


    /**
     * 超过15日订单自动评价
     */
    @XxlJob(value = "autoEvaluate")
    public void autoEvaluate() {

        //每次批量查询订单数量
        Integer autoEvaluateCount = ordersJobProperties.getAutoEvaluateCount();
        while (true) {
            List<Orders> ordersList = ordersManagerService.queryOverTimeEvaluateOrdersList(autoEvaluateCount);
            if (CollUtil.isEmpty(ordersList)) {
                break;
            }

            for (Orders orders : ordersList) {
                //订单评价
                OrdersJob owner = applicationContext.getBean(OrdersJob.class);
                owner.evaluateOrders(orders);
            }
        }
    }


    /**
     * 订单评价
     *
     * @param orders 订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void evaluateOrders(Orders orders) {
        //发起评价
        EvaluationSubmitReqDTO evaluationSubmitReqDTO = new EvaluationSubmitReqDTO();
        evaluationSubmitReqDTO.setUserType(UserType.C_USER);
        evaluationSubmitReqDTO.setUserId(orders.getUserId());
        evaluationSubmitReqDTO.setOrdersId(orders.getId());
        evaluationApi.autoEvaluate(evaluationSubmitReqDTO);
        //订单已评价
        ordersManagerService.evaluationOrder(orders.getId());
//        orderStateMachine.changeStatus(orders.getUserId(), orders.getId().toString(), OrderStatusChangeEventEnum.EVALUATE);
    }

    /**
     * 派单超时订单取消
     */
    @XxlJob(value = "cancelDispatchOverTimeOrders")
    public void cancelDispatchOverTimeOrders() {

        //查询派单超时订单
        Integer count = ordersJobProperties.getDispatchOverTimeOrderCount();
        List<Orders> ordersList = ordersManagerService.queryDispatchOverTimeOrdersList(count);
        if (CollUtil.isEmpty(ordersList)) {
            XxlJobHelper.log("查询到派单超时订单列表为空！");
            return;
        }

        for (Orders orders : ordersList) {
            //取消订单
            OrderCancelReqDTO orderCancelDTO = BeanUtil.toBean(orders, OrderCancelReqDTO.class);
            orderCancelDTO.setCancelReason("派单超时取消");
            ordersManagerService.cancel(orderCancelDTO);
        }
    }

}
