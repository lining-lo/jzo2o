package com.jzo2o.orders.manager.client;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.jzo2o.api.customer.AddressBookApi;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class CustomerClient {

    @Resource
    private AddressBookApi addressBookApi;

    @SentinelResource(value = "getAddressBookDetail", fallback = "findAddressDetailByIdFallback", blockHandler = "getAddressBookDetailBlockHandler")
    public AddressBookResDTO findAddressDetailById(Long id) {
        log.info("根据id查询地址簿，id:{}", id);
        // 调用其他微服务方法
        AddressBookResDTO detail = addressBookApi.findById(id);
        return detail;
    }

    //执行异常走
    public AddressBookResDTO findAddressDetailByIdFallback(Long id, Throwable throwable) {
        log.error("非限流、熔断等导致的异常执行的降级方法，id:{},throwable:", id, throwable);
        return null;
    }

    //熔断后的降级逻辑
    public AddressBookResDTO getAddressBookDetailBlockHandler(Long id, BlockException blockException) {
        log.error("触发限流、熔断时执行的降级方法，id:{},blockException:", id, blockException);
        return null;
    }
}