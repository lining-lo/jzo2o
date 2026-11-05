package com.jzo2o.orders.manager.client;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.market.CouponApi;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 服务保护组件类：保护market的Feign调用过程
 */
@Slf4j
@Component
public class MarketClient {

    @Autowired
    private CouponApi couponApi;


    /**
     * sentinel资源方法
     * @param totalAmount
     * @return
     */
    @SentinelResource(value = "addressDetail", fallback = "getAvailableFallback", blockHandler = "getAvailableBlockHandler")
    public List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount){
        List<AvailableCouponsResDTO> couponsResDTOList = couponApi.getAvailable(totalAmount);
        return couponsResDTOList;
    }

    /**
     * 非熔断限流造成的异常而执行的降级方法
     * @param totalAmount
     * @param throwable
     * @return
     */
    public List<AvailableCouponsResDTO> getAvailableFallback(BigDecimal totalAmount, Throwable throwable) {
        log.error("[customer的feign接口]非熔断限流造成的异常而执行的降级方法", throwable);
        return null;
    }

    /**
     * 触发熔断或限流执行的降级方法
     * @param totalAmount
     * @param e
     * @return
     */
    public List<AvailableCouponsResDTO> getAvailableBlockHandler(BigDecimal totalAmount, BlockException e) {
        log.error("[customer的feign接口]触发熔断或限流执行的降级方法", e);
        return null;
    }
}