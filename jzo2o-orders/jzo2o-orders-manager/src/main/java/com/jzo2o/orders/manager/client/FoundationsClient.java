package com.jzo2o.orders.manager.client;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class FoundationsClient {
    @Resource
    private ServeApi serveApi;

    @SentinelResource(value = "findServeDetailById", fallback = "findByIdFallback", blockHandler = "findByIdBlockHandler")
    public ServeAggregationResDTO findServeDetailById(Long id) {
        return serveApi.findById(id);
    }

    private ServeAggregationResDTO findByIdFallback(Long id) {
        return new ServeAggregationResDTO();
    }

    private ServeAggregationResDTO findByIdBlockHandler(Long id, Throwable e) {
        return new ServeAggregationResDTO();
    }
}
