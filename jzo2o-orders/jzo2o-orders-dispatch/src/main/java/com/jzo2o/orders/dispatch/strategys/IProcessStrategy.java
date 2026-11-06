package com.jzo2o.orders.dispatch.strategys;

import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;

import java.util.List;

/**
 * 策略接口类
 *
 * @Author mr.wu
 * @Date 2024-12-27 14:30
 */
public interface IProcessStrategy {

    /**
     * 根据策略获取服务人员/机构
     * @param serveProviderDTOList
     * @return
     */
    ServeProviderDTO getPrecedenceServeProvider(List<ServeProviderDTO> serveProviderDTOList);
}
