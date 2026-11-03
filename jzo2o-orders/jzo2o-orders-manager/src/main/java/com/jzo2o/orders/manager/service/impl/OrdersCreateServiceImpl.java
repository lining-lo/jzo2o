package com.jzo2o.orders.manager.service.impl;

import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.api.trade.NativePayApi;
import com.jzo2o.api.trade.TradingApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.api.trade.dto.response.TradingResDTO;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.constants.MqConstants;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.IdUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.config.OrderStateMachine;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.manager.client.CustomerClient;
import com.jzo2o.orders.manager.client.FoundationsClient;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.rabbitmq.client.RabbitClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.bouncycastle.asn1.x500.style.RFC4519Style.owner;

/**
 * <p>
 * 下单服务类
 * </p>
 */
@Slf4j
@Service
public class OrdersCreateServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersCreateService {

    private static final String ORDER_SERIAL_NUM_KEY = "ORDER:SERIAL:NUM";
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private CustomerClient customerClient;
    @Autowired
    private FoundationsClient foundationsClient;
    @Autowired
    private IOrdersCreateService owner;
    @Autowired
    private TradeProperties tradeProperties;
    @Autowired
    private NativePayApi nativePayApi;
    @Autowired
    private TradingApi tradingApi;
    @Autowired
    private RabbitClient rabbitClient;
    @Autowired
    private OrderStateMachine orderStateMachine;

    /**
     * 生成唯一订单号（yyMMdd+13位自增序号）
     *
     * @return
     */
    @Override
    public Long generateOrderId() {
        Long serialNum = redisTemplate.opsForValue().increment(ORDER_SERIAL_NUM_KEY);
        Long id = DateUtils.getFormatDate(LocalDateTime.now(), "yyMMdd") * 10000000000000L + serialNum;
        return id;
    }

    /**
     * 预约下单
     *
     * @param dto
     * @return
     */
    @Override
    public PlaceOrderResDTO placeOrder(PlaceOrderReqDTO dto) {
        //1. 调用feign接口（foundations服务的）查询服务信息
        ServeAggregationResDTO serve = foundationsClient.findServeDetailById(dto.getServeId());
        if (serve == null || serve.getSaleStatus() != 2) {
            throw new BadRequestException("非法服务");
        }

        //2. 调用feign接口（customer服务的）查询地址信息
        AddressBookResDTO address = customerClient.findAddressDetailById(dto.getAddressBookId());
        if (address == null) {
            throw new BadRequestException("没有选择地址");
        }

        //3. 封装订单对象信息
        Orders orders = new Orders();

        //3.1 订单基本信息封装
        orders.setId(this.generateOrderId());//订单号：19位组成结构 - 2位年+2位月+2位日+13位序号
        orders.setOrdersStatus(OrderStatusEnum.NO_PAY.getStatus()); // 订单状态代码，下单后状态是未支付状态
        orders.setPayStatus(OrderPayStatusEnum.NO_PAY.getStatus()); // 订单支付状态代码，下单后状态是未支付状态 2：待支付，4：支付成功
        orders.setDisplay(1); //用户端是否展示，1：展示，0：隐藏，默认展示
        orders.setServeStartTime(dto.getServeStartTime());//服务开始时间
        orders.setSortBy(DateUtils.toEpochMilli(dto.getServeStartTime()) + orders.getId() % 10000); //根据服务开始时间转为毫秒时间戳+订单后5位

        //3.2 下单人信息封装
        orders.setUserId(UserContext.currentUser().getId());//下单人id
        String serveAddress = new StringBuilder()
                .append(address.getProvince()) //省
                .append(address.getCity())//市
                .append(address.getCounty()) //区
                .append(address.getAddress()) //详细地址
                .toString();
        orders.setServeAddress(serveAddress); // 家政服务的具体地址（省+市+区+详细地址）
        orders.setContactsPhone(address.getPhone());//下单人手机号
        orders.setContactsName(address.getName());//下单人姓名
        orders.setLat(address.getLat());//维度
        orders.setLon(address.getLon());//精度

        //3.3  支付相关信息（暂时不处理）

        //3.4 服务信息封装
        orders.setServeId(serve.getId());//服务id
        orders.setPrice(serve.getPrice());//服务单价
        orders.setCityCode(serve.getCityCode());//城市编码
        orders.setServeItemId(serve.getServeItemId());//服务项id
        orders.setServeItemName(serve.getServeItemName());//服务项名称
        orders.setServeItemImg(serve.getServeItemImg());//服务项图片
        orders.setServeTypeId(serve.getServeTypeId());//服务类型id
        orders.setServeTypeName(serve.getServeTypeName());//服务类型名称
        orders.setUnit(serve.getUnit());//服务单位

        //3.5 价格信息封装
        orders.setPurNum(dto.getPurNum());//服务数量
        orders.setTotalAmount(serve.getPrice().multiply(BigDecimal.valueOf(dto.getPurNum())));//订单总金额 价格 * 购买数量
        orders.setDiscountAmount(BigDecimal.ZERO);//优惠金额，暂时为0
        orders.setRealPayAmount(orders.getTotalAmount().subtract(orders.getDiscountAmount()));//实际支付金额=订单总金额-优惠金额

        //4. 创建订单到订单表
        owner.add(orders);

        //5. 生产延迟15分钟消费的消息
        rabbitClient.sendMsg(MqConstants.Exchanges.ORDERS,
                MqConstants.RoutingKeys.ORDERS_CANCEL_KEY,
                orders.getId().toString(), 60000,//为了方便测试这里写了延迟60秒
                IdUtils.getSnowflakeNextId(), false);

        return new PlaceOrderResDTO(orders.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void add(Orders orders) {
        boolean result = this.save(orders);
        if(!result) {
            throw new DBException("服务预约下单失败");
        }
        OrderSnapshotDTO orderSnapshotDTO = BeanUtils.copyBean(orders,OrderSnapshotDTO.class);
        orderStateMachine.start(orders.getUserId(),orders.getId().toString(), orderSnapshotDTO);
    }

    /**
     * 订单支付
     *
     * @param id
     * @param dto
     * @return
     */
    @Override
    public OrdersPayResDTO pay(Long id, OrdersPayReqDTO dto) {
        //1. 判断订单是否存在
        Orders orders = this.getById(id);
        if (orders == null) {
            throw new BadRequestException("订单不存在");
        }

        //2. 判断订单是否已支付成功（支付状态和交易单号）
        if (ObjectUtils.equals(orders.getPayStatus(), OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                && ObjectUtils.isNotNull(orders.getTradingOrderNo())) {
            OrdersPayResDTO ordersPayResDTO = BeanUtils.copyBean(orders, OrdersPayResDTO.class);
            ordersPayResDTO.setProductOrderNo(id);
            return ordersPayResDTO;
        }

        //3. 调用支付服务的Feign接口，获取响应数据

        //3.1 根据支付渠道获取商户号
        Long enterpriseId = ObjectUtils.equals(dto.getTradingChannel(), PayChannelEnum.WECHAT_PAY) ? tradeProperties.getWechatEnterpriseId()
                : tradeProperties.getAliEnterpriseId();

        //3.2 判断是否更换过支付渠道 （订单已有支付渠道，且与本次请求参数的支付渠道不一样，就算更换过）
        boolean changeChannel = false; //默认值：未更换
        if (ObjectUtils.isNotNull(orders.getTradingChannel()) && ObjectUtils.notEqual(orders.getTradingChannel(), dto.getTradingChannel())) {
            changeChannel = true;
        }

        //业务系统唯一标识
        String productAppId = tradeProperties.getProductAppId();

        //3.3 发起feign接口请求调用支付服务，获取响应数据
        NativePayReqDTO nativePayReqDTO = new NativePayReqDTO();
        nativePayReqDTO.setProductOrderNo(id); //业务系统订单号（订单表的订单ID值）
        nativePayReqDTO.setEnterpriseId(enterpriseId);//商户号
        nativePayReqDTO.setProductAppId(productAppId);//业务系统唯一标识
        nativePayReqDTO.setChangeChannel(changeChannel);//是否更换过支付渠道
        nativePayReqDTO.setMemo(orders.getServeItemName());//商品描述
        //nativePayReqDTO.setTradingAmount(orders.getRealPayAmount());//订单金额：实际支付金额
        nativePayReqDTO.setTradingAmount(BigDecimal.valueOf(0.01));
        nativePayReqDTO.setTradingChannel(dto.getTradingChannel());//支付渠道

        NativePayResDTO nativePayResDTO = nativePayApi.createDownLineTrading(nativePayReqDTO);
        if (ObjectUtils.isNull(nativePayResDTO)) {
            throw new BadRequestException("调用支付服务的支付接口失败");
        }

        //4. 更新订单表数据（支付渠道和交易单号） update orders set trading_channel=?,trading_order_no=? where id=?
        owner.updateOrders(id, nativePayResDTO);

        //5. 封装响应数据
        OrdersPayResDTO ordersPayResDTO = BeanUtils.copyBean(nativePayResDTO, OrdersPayResDTO.class);
        ordersPayResDTO.setPayStatus(orders.getPayStatus());
        return ordersPayResDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrders(Long orderId, NativePayResDTO nativePayResDTO) {
        boolean result = this.lambdaUpdate().eq(Orders::getId, orderId)
                .set(Orders::getTradingChannel, nativePayResDTO.getTradingChannel())
                .set(Orders::getTradingOrderNo, nativePayResDTO.getTradingOrderNo())
                .update();
        if (!result) {
            throw new DBException("支付失败");
        }
    }

    /**
     * 查询订单支付结果
     *
     * @param id
     * @return
     */
    @Override
    public OrdersPayResDTO getPayResultFromTradServer(Long id) {
        //1.判断订单是否存在
        Orders orders = this.getById(id);
        if (orders == null) {
            throw new BadRequestException("订单不存在");
        }

        //2.判断是否有交易单号
        if (ObjectUtils.isNull(orders.getTradingOrderNo())) {
            throw new BadRequestException("非法订单");
        }

        //3.判断是否支付成功
        OrdersPayResDTO ordersPayResDTO = BeanUtils.copyBean(orders, OrdersPayResDTO.class);
        ordersPayResDTO.setProductOrderNo(id);
        if (ObjectUtils.equals(orders.getPayStatus(), OrderPayStatusEnum.PAY_SUCCESS.getStatus())) {
            return ordersPayResDTO;
        }

        //4.调用支付服务Feign接口查询支付结果
        TradingResDTO tradingResDTO = tradingApi.findTradResultByTradingOrderNo(orders.getTradingOrderNo());

        //5.更新订单表信息（支付时间、第三方交易号、订单状态、支付状态）
        //update orders set pay_time=?,transaction_id=?,order_status=?,pay_status=? where id=?
        if (ObjectUtils.isNotNull(tradingResDTO) && ObjectUtils.equals(tradingResDTO.getTradingState(), TradingStateEnum.YJS)) {
            owner.paySuccess(id, tradingResDTO.getTransactionId(), tradingResDTO.getPaySuccessTime());

            //6.封装响应数据
            ordersPayResDTO.setQrCode(tradingResDTO.getQrCode());//支付二维码图片地址
            ordersPayResDTO.setPayStatus(OrderPayStatusEnum.PAY_SUCCESS.getStatus());//支付成功
        }
        return ordersPayResDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(Long orderId, String transactionId, LocalDateTime payTime) {
        //构建快照数据
        OrderSnapshotDTO orderSnapshotDTO = new OrderSnapshotDTO();
        orderSnapshotDTO.setId(orderId);
        orderSnapshotDTO.setTransactionId(transactionId);
        orderSnapshotDTO.setPayTime(payTime);

        Orders orders = this.getById(orderId);
        //调用状态机的变更状态方法（表明触发支付成功事件）
        orderStateMachine.changeStatus(orders.getUserId(), orderId.toString(), OrderStatusChangeEventEnum.PAYED, orderSnapshotDTO);

    }
}

