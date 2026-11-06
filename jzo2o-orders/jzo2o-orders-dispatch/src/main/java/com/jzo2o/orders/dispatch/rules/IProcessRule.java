package com.jzo2o.orders.dispatch.rules;

import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;

import java.util.List;

/**
 * 处理规则接口类
 *
 * @Author mr.wu
 * @Date 2024-12-27 10:54
 */
public interface IProcessRule {


    /**
     * 筛选出符合规则的服务人员或机构
     * @param providerDTOList
     * @return
     */
    List<ServeProviderDTO> filter(List<ServeProviderDTO> providerDTOList);


    /**
     * 链中的下个一个规则处理类对象
     * @return
     */
    IProcessRule next();
}
