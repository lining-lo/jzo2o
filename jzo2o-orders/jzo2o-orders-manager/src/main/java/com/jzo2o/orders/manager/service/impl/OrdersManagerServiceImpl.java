package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.InstitutionStaffApi;
import com.jzo2o.api.customer.ServeProviderApi;
import com.jzo2o.api.customer.dto.response.InstitutionStaffResDTO;
import com.jzo2o.api.customer.dto.response.ServeProviderResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.orders.dto.request.OrderCancelReqDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.constants.UserType;
import com.jzo2o.common.enums.EnableStatusEnum;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.JsonUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.mysql.utils.PageUtils;
import com.jzo2o.orders.base.constants.RedisConstants;
import com.jzo2o.orders.base.enums.EvaluationStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersServe;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.statemachine.OrderStateMachine;
import com.jzo2o.orders.manager.client.FoundationsClient;
import com.jzo2o.orders.manager.client.MarketClient;
import com.jzo2o.orders.manager.handler.OrdersJob;
import com.jzo2o.orders.manager.model.dto.request.OrderPageQueryReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OperationOrdersDetailResDTO;
import com.jzo2o.orders.manager.service.IOrdersCanceledService;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersServeManagerService;
import com.jzo2o.redis.helper.CacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.jzo2o.orders.base.constants.FieldConstants.SORT_BY;
import static com.jzo2o.orders.base.constants.RedisConstants.RedisKey.ORDERS;

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

    @Override
    public List<Orders> batchQuery(List<Long> ids) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery().in(Orders::getId, ids).ge(Orders::getUserId, 0);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Orders queryById(Long id) {
        return baseMapper.selectById(id);
    }

    @Autowired
    private CacheHelper cacheHelper;

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
                .eq(Orders::getDisplay, EnableStatusEnum.ENABLE.getStatus())
                .select(Orders::getId) //只查询ID字段（目的是为了使用覆盖索引查询，避免回表查询，提升查询效率）
                ;
        Page<Orders> queryPage = new Page<>();
        queryPage.addOrder(OrderItem.desc(SORT_BY));
        queryPage.setSearchCount(false);

        //2.查询订单列表
        Page<Orders> ordersPage = baseMapper.selectPage(queryPage, queryWrapper);
        List<Orders> records = ordersPage.getRecords();

        //3.获取订单ID列表
        //List<Long> orderIdList = CollUtils.getFieldValues(records,Orders::getId);
        List<Long> orderIdList = records.stream().map(Orders::getId).collect(Collectors.toList());
        if(CollUtils.isEmpty(orderIdList)) {
            return CollUtils.emptyList();
        }

        //4.通过订单ID列表批量查询订单
//        List<Orders> ordersList = batchQuery(orderIdList);
//
//        List<OrderSimpleResDTO> orderSimpleResDTOS = BeanUtil.copyToList(ordersList, OrderSimpleResDTO.class);

        //1. 拼接RedisKey后缀
        String redisKeyPostfix = String.format(ORDERS,currentUserId);


        //2. 调用工具类的方法执行查询（根据没有命中缓存的ID从数据库查询数据，再次追加到缓存中）
        List<OrderSimpleResDTO> orderSimpleResDTOList = cacheHelper.<Long, OrderSimpleResDTO>batchGet(redisKeyPostfix, orderIdList,
                (nocacheIds, clazz) -> {
                    //1.根据订单ID从数据库查询订单列表
                    List<Orders> ordersList = batchQuery(nocacheIds);
                    if(CollUtils.isEmpty(ordersList)) {
                        return new HashMap<>();
                    }

                    //2.将订单列表转为HashMap（key：订单id  value：OrderSimpleResDTO对象）
                    Map<Long, OrderSimpleResDTO> orderMap = ordersList.stream().collect(Collectors.toMap(Orders::getId, o -> BeanUtils.copyBean(o, OrderSimpleResDTO.class)));

                    return orderMap;
                },
                OrderSimpleResDTO.class, RedisConstants.Ttl.ORDERS_PAGE_TTL);

        //3. 返回数据
        return orderSimpleResDTOList;
    }

    @Autowired
    private OrderStateMachine orderStateMachine;

    /**
     * 根据订单id查询
     *
     * @param id 订单id
     * @return 订单详情
     */
    @Override
    public OrderResDTO getDetail(Long id) {
//        Orders orders = queryById(id);
//        OrderResDTO orderResDTO = BeanUtil.toBean(orders, OrderResDTO.class);
        String snapshotJSON = orderStateMachine.getCurrentSnapshotCache(id.toString());
        OrderSnapshotDTO orderSnapshotDTO = JsonUtils.toBean(snapshotJSON, OrderSnapshotDTO.class);
        return BeanUtils.copyBean(orderSnapshotDTO, OrderResDTO.class);
    }



    private List<Integer> cancelStatusList = Arrays.asList(OrderStatusEnum.NO_PAY.getStatus(), OrderStatusEnum.DISPATCHING.getStatus());

    @Autowired
    private IOrdersManagerService owner;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 取消订单
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancel(OrderCancelReqDTO dto) {
        //1.判断订单是否存在
        Orders orders = this.getById(dto.getId());
        if(orders==null) {
            throw new BadRequestException("订单不存在");
        }

        //2.判断订单状态是否合法（待支付、待派单）
        if(!cancelStatusList.contains(orders.getOrdersStatus())){
            throw new BadRequestException("非法订单");
        }

        //3.如果是待支付订单（则添加取消记录、更新订单状态）
        if(ObjectUtils.equals(orders.getOrdersStatus(),OrderStatusEnum.NO_PAY.getStatus())) {
            owner.cancelOrderByNoPay(orders.getId(), dto.getCancelReason(), false);
        } else {
            //4.如果是待派单订单（则添加取消记录、更新订单状态、发起退款申请及后续处理）
            OrdersJob ordersJob = applicationContext.getBean(OrdersJob.class);
            ordersJob.cancelOrderByDispatching(orders, dto.getCancelReason(), UserContext.currentUser());
        }

    }

    @Autowired
    private IOrdersCanceledService ordersCanceledService;


    /**
     * 取消未支付订单
     *
     * @param orderId      订单ID
     * @param cancelReason 取消原因
     * @param isSystem 是否系统自动触发 true-系统触发 false-人为触发
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelOrderByNoPay(Long orderId, String cancelReason, boolean isSystem) {
        //1. 添加取消记录
        OrdersCanceled ordersCanceled = new OrdersCanceled();
        ordersCanceled.setId(orderId);//订单id
        if(!isSystem) {
            ordersCanceled.setCancellerId(UserContext.currentUserId());//取消人id
            ordersCanceled.setCancellerType(UserContext.currentUser().getUserType());//取消人类型
            ordersCanceled.setCancelerName(UserContext.currentUser().getName());//取消人姓名
        }
        ordersCanceled.setCancelReason(cancelReason);//取消原因
        ordersCanceled.setCancelTime(LocalDateTime.now());//取消时间
        boolean resultForCancel = ordersCanceledService.save(ordersCanceled);
        if(!resultForCancel) {
            throw new DBException("添加取消记录失败");
        }

        Orders orders = this.getById(orderId);
        //2. 触发取消事件，变更订单状态为已取消
        orderStateMachine.changeStatus(orders.getUserId(), orderId.toString(), OrderStatusChangeEventEnum.CANCEL, null);
    }

    @Autowired
    private MarketClient marketClient;

    @Autowired
    private FoundationsClient foundationsClient;

    /**
     * 获取可用优惠券
     *
     * @param serveId
     * @param purNum
     * @return
     */
    @Override
    public List<AvailableCouponsResDTO> getCoupons(Long serveId, Integer purNum) {
        //1.查询服务判断是否存在
        ServeAggregationResDTO serve = foundationsClient.findServeDetailById(serveId);
        if(serve==null) {
            throw new BadRequestException("服务不存在");
        }

        //2.根据服务单价和购买数量计算订单总金额
        BigDecimal totalAmount = serve.getPrice().multiply(BigDecimal.valueOf(purNum));


        //3. 调用feign接口查询可用优惠券列表
        List<AvailableCouponsResDTO> couponsResDTOList = marketClient.getAvailable(totalAmount);
        return couponsResDTOList;
    }


    /**
     * 用户端-订单显示状态设置
     *
     * @param id            订单id
     * @param displayStatus 用户端是否展示，1：展示，0：隐藏
     */
    @Override
    public void displaySetting(Long id, Integer displayStatus) {
        LambdaUpdateWrapper<Orders> updateWrapper = Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, id)
                .ge(Orders::getUserId, 0)
                .set(Orders::getDisplay, displayStatus);
        super.update(updateWrapper);
    }

   /**
    * 根据订单id列表查询并排序
    * @param orderPageQueryReqDTO 订单分页查询请求
    * @return 订单列表
    */
    @Override
    public List<Orders> queryAndSortOrdersListByIds(OrderPageQueryReqDTO orderPageQueryReqDTO) {
        //1.构造查询条件
        Page<Orders> page = new Page<>();
        page.setSize(orderPageQueryReqDTO.getPageSize());
        page.setOrders(PageUtils.getOrderItems(orderPageQueryReqDTO, Orders.class));
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .in(Orders::getId, orderPageQueryReqDTO.getOrdersIdList())
                .eq(ObjectUtils.isNotNull(orderPageQueryReqDTO.getUserId()), Orders::getUserId, orderPageQueryReqDTO.getUserId())
                .gt(ObjectUtils.isNull(orderPageQueryReqDTO.getUserId()), Orders::getUserId, 0);

        //2.分页查询
        page.setSearchCount(false);
        Page<Orders> ordersPage = baseMapper.selectPage(page, queryWrapper);
        if (ObjectUtil.isEmpty(ordersPage.getRecords())) {
            return Collections.emptyList();
        }

        return ordersPage.getRecords();
    }



    /**
     * 查询超过评价时间的订单
     *
     * @param count 订单数量
     * @return 订单列表
     */
    @Override
    public List<Orders> queryOverTimeEvaluateOrdersList(Integer count) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrdersStatus, OrderStatusEnum.FINISHED.getStatus())
                //TODO 需要测试，暂时改为半小时
//                .lt(Orders::getUpdateTime, LocalDateTime.now().minusDays(15))
                .lt(Orders::getUpdateTime, LocalDateTime.now().minusMinutes(30))
                .gt(Orders::getId, 0)
                .gt(Orders::getUserId, 0)
                .orderByAsc(Orders::getUpdateTime)
                .last("LIMIT " + count);

        List<Orders> ordersList = baseMapper.selectList(queryWrapper);
        if (ObjectUtil.isEmpty(ordersList)) {
            return Collections.emptyList();
        }

        return ordersList;
    }

    @Autowired
    private IOrdersServeManagerService ordersServeManagerService;

    @Autowired
    private ServeProviderApi serveProviderApi;

    @Autowired
    private InstitutionStaffApi institutionStaffApi;

    /**
     * 订单信息聚合
     *
     * @param id 订单id
     * @return 订单详情
     */
    @Override
    public OperationOrdersDetailResDTO aggregation(Long id) {
        //1.订单信息
        String jsonResult = orderStateMachine.getCurrentSnapshotCache(String.valueOf(id));
        OrderSnapshotDTO orderSnapshotDTO = JSONUtil.toBean(jsonResult, OrderSnapshotDTO.class);
        Orders orders = BeanUtil.toBean(orderSnapshotDTO, Orders.class);

        //订单信息
        OperationOrdersDetailResDTO.OrderInfo orderInfo = BeanUtil.toBean(orderSnapshotDTO, OperationOrdersDetailResDTO.OrderInfo.class);

        //支付信息
        OperationOrdersDetailResDTO.PayInfo payInfo = BeanUtil.toBean(orderSnapshotDTO, OperationOrdersDetailResDTO.PayInfo.class);

        //退款信息
        OperationOrdersDetailResDTO.RefundInfo refundInfo = BeanUtil.toBean(orderSnapshotDTO, OperationOrdersDetailResDTO.RefundInfo.class);

        //订单取消信息
        OperationOrdersDetailResDTO.CancelInfo cancelInfo = BeanUtil.toBean(orderSnapshotDTO, OperationOrdersDetailResDTO.CancelInfo.class);

        //3.服务单信息
        OperationOrdersDetailResDTO.ServeInfo serveInfo = new OperationOrdersDetailResDTO.ServeInfo();
        OrdersServe ordersServe = ordersServeManagerService.queryById(id);
        if (ObjectUtil.isNotEmpty(ordersServe)) {
            //用户类型，2：服务人员，3：机构
            Integer serveProviderType = ordersServe.getServeProviderType();

            //服务人员/机构名称
            ServeProviderResDTO serveProviderResDTO = serveProviderApi.getDetail(ordersServe.getServeProviderId());
            String serveProviderName = serveProviderResDTO.getName();

            //机构下属员工姓名
            String institutionStaffName = null;
            String institutionStaffPhone = null;
            if (ObjectUtil.equal(ordersServe.getServeProviderType(), UserType.INSTITUTION) && null != ordersServe.getInstitutionStaffId()) {
                InstitutionStaffResDTO institutionStaffResDTO = institutionStaffApi.findByIdAndInstitutionId(ordersServe.getInstitutionStaffId(), ordersServe.getServeProviderId());
                institutionStaffName = institutionStaffResDTO.getName();
                institutionStaffPhone = institutionStaffResDTO.getPhone();
            }

            //组装服务信息
            serveInfo.setServeProviderType(serveProviderType);
            serveInfo.setServeProviderName(serveProviderName);
            serveInfo.setInstitutionStaffName(institutionStaffName);

            serveInfo.setRealServeStartTime(ordersServe.getRealServeStartTime());
            serveInfo.setServeBeforeIllustrate(ordersServe.getServeBeforeIllustrate());
            serveInfo.setServeBeforeImgs(ordersServe.getServeBeforeImgs());

            serveInfo.setRealServeEndTime(ordersServe.getRealServeEndTime());
            serveInfo.setServeAfterIllustrate(ordersServe.getServeAfterIllustrate());
            serveInfo.setServeAfterImgs(ordersServe.getServeAfterImgs());

            //组装订单中的服务人员信息
            orderInfo.setServeProviderType(serveProviderType);
            orderInfo.setServeProviderName(serveProviderName);
            orderInfo.setServeProviderPhone(serveProviderResDTO.getPhone());
            orderInfo.setInstitutionStaffName(institutionStaffName);
            orderInfo.setInstitutionStaffPhone(institutionStaffPhone);
        }

        //4.处理订单进度条
        List<OperationOrdersDetailResDTO.OrderProgress> orderProgresses = orderProgressHandler(orders, ordersServe, orderSnapshotDTO.getCancelTime());

        OperationOrdersDetailResDTO operationOrdersDetailResDTO = new OperationOrdersDetailResDTO();
        operationOrdersDetailResDTO.setOrdersProgress(orderProgresses);
        operationOrdersDetailResDTO.setOrderInfo(orderInfo);
        operationOrdersDetailResDTO.setPayInfo(payInfo);
        operationOrdersDetailResDTO.setServeInfo(serveInfo);
        operationOrdersDetailResDTO.setRefundInfo(refundInfo);
        operationOrdersDetailResDTO.setCancelInfo(cancelInfo);
        return operationOrdersDetailResDTO;
    }


    /**
     * 计算订单进度条
     *
     * @param orders      订单
     * @param ordersServe 服务单
     * @return 订单进度条
     */
    private List<OperationOrdersDetailResDTO.OrderProgress> orderProgressHandler(Orders orders, OrdersServe ordersServe, LocalDateTime cancelTime) {
        List<OperationOrdersDetailResDTO.OrderProgress> orderProgressList = new ArrayList<>();
        LocalDateTime payTime = orders.getPayTime();

        //1.异常过程
        OperationOrdersDetailResDTO.OrderProgress exceptionProgress = null;
        boolean isException = OrderStatusEnum.CANCELED.getStatus().equals(orders.getOrdersStatus()) || OrderStatusEnum.CLOSED.getStatus().equals(orders.getOrdersStatus());
        if (isException) {
            //如果支付时间不为空，则为退款状态，否则为取消状态
            Integer exceptionStatus = ObjectUtil.isNotEmpty(payTime) ? OrderStatusEnum.CLOSED.getStatus() : OrderStatusEnum.CANCELED.getStatus();

            exceptionProgress = new OperationOrdersDetailResDTO.OrderProgress(exceptionStatus, cancelTime);
        }

        //2.支付过程
        //如果支付时间不为空，则为已支付（派单中）状态，否则为待支付状态
        Integer payStatus = ObjectUtil.isNotEmpty(payTime) ? OrderStatusEnum.DISPATCHING.getStatus() : OrderStatusEnum.NO_PAY.getStatus();
        LocalDateTime payDateTime = ObjectUtil.isNotEmpty(payTime) ? payTime : orders.getCreateTime();

        OperationOrdersDetailResDTO.OrderProgress payProgress = new OperationOrdersDetailResDTO.OrderProgress(payStatus, payDateTime);
        orderProgressList.add(payProgress);

        //2.1支付状态为空说明未到达派单过程，直接返回
        if (ObjectUtil.isEmpty(payTime)) {
            exceptionProgressJudge(orderProgressList, exceptionProgress);
            return orderProgressList;
        }

        //3.派单过程
        //如果存在服务单，则为派单成功（待服务）状态，否则为派单中状态
        Integer dispatchStatus = null != ordersServe ? OrderStatusEnum.NO_SERVE.getStatus() : OrderStatusEnum.DISPATCHING.getStatus();
        LocalDateTime dispatchDateTime = null != ordersServe ? ordersServe.getCreateTime() : payTime;

        OperationOrdersDetailResDTO.OrderProgress dispatchProgress = new OperationOrdersDetailResDTO.OrderProgress(dispatchStatus, dispatchDateTime);
        orderProgressList.add(dispatchProgress);

        //3.1实际开始服务时间为空说明未到达服务过程，直接返回
        if (null == ordersServe || ObjectUtil.isEmpty(ordersServe.getRealServeStartTime())) {
            exceptionProgressJudge(orderProgressList, exceptionProgress);
            return orderProgressList;
        }

        //4.服务过程
        //如果存在实际服务完成时间，则为订单完成状态，否则为开始服务（服务中）状态
        Integer serveStatus = ObjectUtil.isNotEmpty(ordersServe.getRealServeEndTime()) ? OrderStatusEnum.FINISHED.getStatus() : OrderStatusEnum.SERVING.getStatus();
        LocalDateTime serveDateTime = ObjectUtil.isNotEmpty(ordersServe.getRealServeEndTime()) ? ordersServe.getRealServeEndTime() : ordersServe.getRealServeStartTime();

        OperationOrdersDetailResDTO.OrderProgress serveProgress = new OperationOrdersDetailResDTO.OrderProgress(serveStatus, serveDateTime);
        orderProgressList.add(serveProgress);

        exceptionProgressJudge(orderProgressList, exceptionProgress);
        return orderProgressList;
    }

    /**
     * 异常过程处理
     *
     * @param orderProgressList 订单进度列表
     * @param exceptionProgress 异常过程
     */
    private void exceptionProgressJudge(List<OperationOrdersDetailResDTO.OrderProgress> orderProgressList, OperationOrdersDetailResDTO.OrderProgress exceptionProgress) {
        if (ObjectUtil.isNotEmpty(exceptionProgress)) {
            orderProgressList.add(exceptionProgress);
        }
    }

    /**
     * 用户端-订单删除（隐藏）
     *
     * @param id       订单id
     * @param userType 用户类型
     * @param userId   用户id
     */
    @Override
    @Transactional
    public void hide(Long id, Integer userType, Long userId) {
        //1.校验用户类型是否为普通用户
        if (ObjectUtil.notEqual(userType, UserType.C_USER)) {
            throw new ForbiddenOperationException("非普通用户不可操作");
        }

        //2.校验该操作是否为本人
        Orders orders = queryById(id);
        if (ObjectUtil.notEqual(userId, orders.getUserId())) {
            throw new ForbiddenOperationException("非本人不可操作");
        }

        //3.校验订单状态，只能取消状态、关闭状态才能删除
        if (ObjectUtil.notEqual(OrderStatusEnum.CANCELED.getStatus(), orders.getOrdersStatus()) && ObjectUtil.notEqual(OrderStatusEnum.CLOSED.getStatus(), orders.getOrdersStatus())) {
            throw new ForbiddenOperationException("订单非取消、关闭状态不可操作");
        }

        //4.更新订单显示状态为隐藏
        displaySetting(id, EnableStatusEnum.DISABLE.getStatus());
    }

    /**
     * 管理端 - 分页查询
     *
     * @param orderPageQueryReqDTO 分页查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<OrderSimpleResDTO> operationPageQuery(OrderPageQueryReqDTO orderPageQueryReqDTO) {
        //1.分页查询订单id列表
        Page<Long> ordersIdPage = operationPageQueryOrdersIdList(orderPageQueryReqDTO);
        if (ObjectUtil.isEmpty(ordersIdPage.getRecords())) {
            return PageUtils.toPage(ordersIdPage, OrderSimpleResDTO.class);
        }

        //2.根据订单id列表查询订单
        orderPageQueryReqDTO.setOrdersIdList(ordersIdPage.getRecords());
        List<Orders> ordersList = queryAndSortOrdersListByIds(orderPageQueryReqDTO);
        List<OrderSimpleResDTO> orderSimpleResDTOList = BeanUtil.copyToList(ordersList, OrderSimpleResDTO.class);

        //3.封装响应结果
        return PageResult.<OrderSimpleResDTO>builder()
                .total(ordersIdPage.getTotal())
                .pages(ordersIdPage.getPages())
                .list(orderSimpleResDTOList)
                .build();
    }

    /**
     * 订单评价
     *
     * @param ordersId 订单id
     */
    @Override
    @Transactional
    public void evaluationOrder(Long ordersId) {
        //查询订单详情
        Orders orders = queryById(ordersId);

        if(ObjectUtils.isNull(orders)){
            throw new CommonException("订单不存在");
        }

        //更新订单评价状态
        LambdaUpdateWrapper<Orders> updateWrapper = Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, ordersId)
                .eq(Orders::getOrdersStatus, OrderStatusEnum.FINISHED.getStatus())
                .eq(Orders::getEvaluationStatus, EvaluationStatusEnum.WAIT_EVALUATE.getStatus())
                .set(Orders::getEvaluationStatus, EvaluationStatusEnum.COMPLETE_EVALUATION.getStatus());
        boolean result = super.update(updateWrapper);
        if (!result) {
            throw new DBException("更新评价状态失败");
        }

        //查询快照
        String jsonResult = orderStateMachine.getCurrentSnapshot(ordersId.toString());
        OrderSnapshotDTO orderSnapshotDTO = JSONUtil.toBean(jsonResult, OrderSnapshotDTO.class);

        //清除用户查询订单的的缓存
        String key = String.format(ORDERS, orderSnapshotDTO.getUserId());
        cacheHelper.remove(key, ordersId);

        //评价状态
        orderSnapshotDTO.setEvaluationStatus(EvaluationStatusEnum.COMPLETE_EVALUATION.getStatus());
        //保存快照
        orderStateMachine.saveSnapshot(orderSnapshotDTO.getUserId(), orderSnapshotDTO.getId().toString(), OrderStatusEnum.FINISHED, orderSnapshotDTO);


//        //更新订单评价状态为已评价
//        lambdaUpdate()
//                .set(Orders::getEvaluationStatus,1)
//                .set(Orders::getEvaluationTime,LocalDateTime.now())
//                .eq(Orders::getId,ordersId)
//                .update();
//
//        //构建订单快照
////        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
////                .evaluationTime(LocalDateTime.now())
////                .build();
//
//        //订单状态变更
////        orderStateMachine.changeStatus(orders.getUserId(), orders.getId().toString(), OrderStatusChangeEventEnum.EVALUATE, orderSnapshotDTO);
//



    }

    /**
     * 查询派单超时的订单
     *
     * @param count 订单数量
     * @return 订单列表
     */
    @Override
    public List<Orders> queryDispatchOverTimeOrdersList(Integer count) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrdersStatus, OrderStatusEnum.DISPATCHING.getStatus())
                .lt(Orders::getServeStartTime, LocalDateTime.now())
                .gt(Orders::getId, 0)
                .gt(Orders::getUserId, 0)
                .orderByAsc(Orders::getUpdateTime)
                .last("LIMIT " + count);
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 管理端 - 分页查询订单id列表
     *
     * @param orderPageQueryReqDTO 分页查询模型
     * @return 分页结果
     */
    @Override
    public Page<Long> operationPageQueryOrdersIdList(OrderPageQueryReqDTO orderPageQueryReqDTO) {
        //1.构造查询条件
        Page<Orders> page = PageUtils.parsePageQuery(orderPageQueryReqDTO, Orders.class);
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery()
                .eq(ObjectUtil.isNotEmpty(orderPageQueryReqDTO.getContactsPhone()), Orders::getContactsPhone, orderPageQueryReqDTO.getContactsPhone())
                .eq(ObjectUtil.isNotEmpty(orderPageQueryReqDTO.getOrdersStatus()), Orders::getOrdersStatus, orderPageQueryReqDTO.getOrdersStatus())
                .eq(ObjectUtil.isNotEmpty(orderPageQueryReqDTO.getPayStatus()), Orders::getPayStatus, orderPageQueryReqDTO.getPayStatus())
                .eq(ObjectUtil.isNotEmpty(orderPageQueryReqDTO.getRefundStatus()), Orders::getRefundStatus, orderPageQueryReqDTO.getRefundStatus())
                .between(ObjectUtil.isAllNotEmpty(orderPageQueryReqDTO.getMinCreateTime(), orderPageQueryReqDTO.getMaxCreateTime()), Orders::getCreateTime, orderPageQueryReqDTO.getMinCreateTime(), orderPageQueryReqDTO.getMaxCreateTime())
                .gt(Orders::getUserId, 0)
                .select(Orders::getId);

        if (ObjectUtil.isNotEmpty(orderPageQueryReqDTO.getId())) {
            queryWrapper.eq(Orders::getId, orderPageQueryReqDTO.getId());
        } else {
            queryWrapper.gt(Orders::getId, 0);
        }

        //2.分页查询
        Page<Orders> ordersPage = baseMapper.selectPage(page, queryWrapper);

        //3.封装结果，查询数据为空，直接返回
        Page<Long> orderIdsPage = new Page<>();
        BeanUtil.copyProperties(ordersPage, orderIdsPage, "records");
        if (ObjectUtil.isEmpty(ordersPage.getRecords())) {
            return orderIdsPage;
        }

        //4.查询结果不为空，提取订单id封装
        List<Long> orderIdList = ordersPage.getRecords().stream().map(Orders::getId).collect(Collectors.toList());
        orderIdsPage.setRecords(orderIdList);
        return orderIdsPage;
    }
}
