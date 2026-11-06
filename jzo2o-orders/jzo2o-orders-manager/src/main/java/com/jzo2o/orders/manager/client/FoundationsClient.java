package com.jzo2o.orders.manager.client;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 服务保护组件类：保护fundations的Feign调用过程
 *
 * @Author mr.wu
 * @Date 2024-12-9 11:55
 */
@Slf4j
@Component
public class FoundationsClient {

    @Autowired
    private ServeApi serveApi;


    /**
     * sentinel资源方法
     * @param id
     * @return
     */
    @SentinelResource(value = "serveDetail", fallback = "serveDetailFallback", blockHandler = "serveDetailBlockHandler")
    public ServeAggregationResDTO findServeDetailById(Long id) {
        ServeAggregationResDTO address = serveApi.findById(id);
        log.info("[fundations的feign接口]查询地址：{}" , address);
        return address;
    }

    /**
     * 非熔断限流造成的异常而执行的降级方法
     * @param id
     * @param throwable
     * @return
     */
    public AddressBookResDTO serveDetailFallback(Long id, Throwable throwable) {
        log.error("[fundations的feign接口]非熔断限流造成的异常而执行的降级方法", throwable);
        return null;
    }

    /**
     * 触发熔断或限流执行的降级方法
     * @param id
     * @param e
     * @return
     */
    public AddressBookResDTO serveDetailBlockHandler(Long id, BlockException e) {
        log.error("[fundations的feign接口]触发熔断或限流执行的降级方法", e);
        return null;
    }
}
