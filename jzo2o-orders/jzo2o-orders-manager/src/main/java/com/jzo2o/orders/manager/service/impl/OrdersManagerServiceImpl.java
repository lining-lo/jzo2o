package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.orders.dto.request.OrderCancelReqDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.api.trade.RefundRecordApi;
import com.jzo2o.api.trade.enums.RefundStatusEnum;
import com.jzo2o.common.enums.EnableStatusEnum;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.jzo2o.orders.base.constants.FieldConstants.SORT_BY;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-07-10
 */
@Slf4j
@Service
public class OrdersManagerServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersManagerService {

    @Autowired
    private IOrdersManagerService owner;
    @Autowired
    private IOrdersCanceledService ordersCanceledService;
    @Autowired
    private IOrdersRefundService ordersRefundService;
    @Autowired
    private RefundRecordApi refundRecordApi;
    @Autowired
    private IOrdersManagerService ordersManagerService;

    @Override
    public List<Orders> batchQuery(List<Long> ids) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery().in(Orders::getId, ids).ge(Orders::getUserId, 0);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Orders queryById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 滚动分页查询
     *
     * @param currentUserId 当前用户id
     * @param ordersStatus  订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：已取消，700：已关闭
     * @param sortBy        排序字段
     * @return 订单列表
     */
    @Override
    public List<OrderSimpleResDTO> consumerQueryList(Long currentUserId, Integer ordersStatus, Long sortBy) {
        //1.构件查询条件
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .eq(ObjectUtils.isNotNull(ordersStatus), Orders::getOrdersStatus, ordersStatus)
                .lt(ObjectUtils.isNotNull(sortBy), Orders::getSortBy, sortBy)
                .eq(Orders::getUserId, currentUserId)
                .eq(Orders::getDisplay, EnableStatusEnum.ENABLE.getStatus());
        Page<Orders> queryPage = new Page<>();
        queryPage.addOrder(OrderItem.desc(SORT_BY));
        queryPage.setSearchCount(false);

        //2.查询订单列表
        Page<Orders> ordersPage = baseMapper.selectPage(queryPage, queryWrapper);
        List<Orders> records = ordersPage.getRecords();
        List<OrderSimpleResDTO> orderSimpleResDTOS = BeanUtil.copyToList(records, OrderSimpleResDTO.class);
        return orderSimpleResDTOS;

    }

    /**
     * 根据订单id查询
     *
     * @param id 订单id
     * @return 订单详情
     */
    @Override
    public OrderResDTO getDetail(Long id) {
        Orders orders = queryById(id);
        OrderResDTO orderResDTO = BeanUtil.toBean(orders, OrderResDTO.class);
        return orderResDTO;
    }

    /**
     * 订单评价
     *
     * @param ordersId 订单id
     */
    @Override
    @Transactional
    public void evaluationOrder(Long ordersId) {
//        //查询订单详情
//        Orders orders = queryById(ordersId);
//
//        //构建订单快照
//        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
//                .evaluationTime(LocalDateTime.now())
//                .build();
//
//        //订单状态变更
//        orderStateMachine.changeStatus(orders.getUserId(), orders.getId().toString(), OrderStatusChangeEventEnum.EVALUATE, orderSnapshotDTO);
    }

    /**
     * 取消订单
     *
     * @param dto
     */
    @Override
    public void cancel(OrderCancelReqDTO dto) {
        //1. 判断订单是否存在
        Orders orders = this.getById(dto.getId());
        if (orders == null) {
            throw new BadRequestException("订单不存在");
        }

        Integer status = orders.getOrdersStatus();

        //2. 判断订单状态是否合法（必须是未支付或派单中）
        Integer noPayStatus = OrderStatusEnum.NO_PAY.getStatus();
        Integer dispatchingStatus = OrderStatusEnum.DISPATCHING.getStatus();
        List<Integer> statusList = Arrays.asList(noPayStatus, dispatchingStatus);
        if (!statusList.contains(status)) {
            throw new BadRequestException("非法状态");
        }

        //3. 如果是未支付 - 取消订单
        if (status.equals(noPayStatus)) {
            owner.cancelOrderByNoPay(dto.getId(), dto.getCancelReason(), false);
        } else {
            //4. 如果是待派单 - 关闭订单且退款
            owner.cancelOrderByDispatching(orders, dto.getCancelReason());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByNoPay(Long orderId, String cancelReason, boolean isSystem) {
        //1. 添加取消记录
        OrdersCanceled ordersCanceled = new OrdersCanceled();
        ordersCanceled.setId(orderId);//订单id
        if(!isSystem){
            ordersCanceled.setCancellerId(UserContext.currentUserId()); //取消人id
            ordersCanceled.setCancellerType(UserContext.currentUser().getUserType());//取消人类型
            ordersCanceled.setCancelerName(UserContext.currentUser().getName());//取消人姓名
        }
        ordersCanceled.setCancelReason(cancelReason);//取消原因
        ordersCanceled.setCancelTime(LocalDateTime.now());//取消时间
        boolean resultCancel = ordersCanceledService.save(ordersCanceled);
        if(!resultCancel) {
            throw new DBException("添加取消记录失败");
        }

        //2. 更新订单状态 update orders set order_status=? where id=?
        boolean resultOrder = this.lambdaUpdate()
                .eq(Orders::getId, orderId)
                .set(Orders::getOrdersStatus, OrderStatusEnum.CANCELED.getStatus())
                .update();
        if(!resultOrder) {
            throw new DBException("更新订单失败");
        }
    }

    /**
     * 取消派单中订单
     *
     * @param orders
     * @param cancelReason
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelOrderByDispatching(Orders orders, String cancelReason) {
        //1. 添加取消记录
        OrdersCanceled ordersCanceled = new OrdersCanceled();
        ordersCanceled.setId(orders.getId());//订单id
        ordersCanceled.setCancellerId(UserContext.currentUser().getId()); //取消人id
        ordersCanceled.setCancellerType(UserContext.currentUser().getUserType());//取消人类型
        ordersCanceled.setCancelerName(UserContext.currentUser().getName());//取消人姓名
        ordersCanceled.setCancelReason(cancelReason);//取消原因
        ordersCanceled.setCancelTime(LocalDateTime.now());//取消时间
        boolean resultCancel = ordersCanceledService.save(ordersCanceled);
        if(!resultCancel) {
            throw new DBException("添加取消记录失败");
        }

        //2. 更新订单状态 update orders set order_status=?,refund_status=? where id=?
        boolean resultOrder = ordersManagerService.lambdaUpdate()
                .eq(Orders::getId, orders.getId())
                .set(Orders::getOrdersStatus, OrderStatusEnum.CLOSED.getStatus()) //已关闭
                .set(Orders::getRefundStatus, RefundStatusEnum.SENDING.getCode()) //退款中
                .update();
        if(!resultOrder) {
            throw new DBException("更新订单失败");
        }

        //3. 添加退款记录
        OrdersRefund ordersRefund = new OrdersRefund();
        ordersRefund.setId(orders.getId());
        ordersRefund.setTradingOrderNo(orders.getTradingOrderNo());
        //ordersRefund.setRealPayAmount(orders.getRealPayAmount());
        ordersRefund.setRealPayAmount(BigDecimal.valueOf(0.01));//由于前面测试支付都是0.01，这里为方便演示也只能写0.01。 不要使用new BigDecimal(double)，这种会精度丢失
        boolean resultRefund = ordersRefundService.save(ordersRefund);
        if(!resultRefund) {
            throw new DBException("添加退款记录失败");
        }

        //TODO 4. 申请退款
    }

    @Override
    public void ordersRefundHandle(OrdersRefund ordersRefund) {

    }
}
