package com.jzo2o.orders.manager.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.client.CustomerClient;
import com.jzo2o.orders.manager.client.FoundationsClient;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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


    /**
     * 生成唯一订单号（yyMMdd+13位自增序号）
     *
     * @return
     */
    @Override
    public Long generateOrderId() {
        Long serialNum = redisTemplate.opsForValue().increment(ORDER_SERIAL_NUM_KEY);
        Long id =  DateUtils.getFormatDate(LocalDateTime.now(),"yyMMdd") * 10000000000000L + serialNum;
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
        if(serve == null || serve.getSaleStatus()!=2) {
            throw new BadRequestException("非法服务");
        }

        //2. 调用feign接口（customer服务的）查询地址信息
        AddressBookResDTO address = customerClient.findAddressDetailById(dto.getAddressBookId());
        if(address==null) {
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
        this.save(orders);

        return new PlaceOrderResDTO(orders.getId());
    }
}