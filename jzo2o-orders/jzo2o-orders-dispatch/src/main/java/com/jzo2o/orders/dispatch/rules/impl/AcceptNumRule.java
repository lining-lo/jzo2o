package com.jzo2o.orders.dispatch.rules.impl;

import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;
import com.jzo2o.orders.dispatch.rules.IProcessRule;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 按照接单数匹配的规则处理器类
 *
 * @Author mr.wu
 * @Date 2024-12-27 10:56
 */
public class AcceptNumRule extends AbstractProcessRule {

    public AcceptNumRule(IProcessRule next) {
        super(next);
    }

    /**
     * 具体规则处理方法
     * @param providerDTOList
     * @return
     */
    public List<ServeProviderDTO> doFilter(List<ServeProviderDTO> providerDTOList){
        System.out.println("按照接单数优先，选出服务人员");
        if(CollUtils.size(providerDTOList)<2) {
            return providerDTOList;
        }

        //根据接单数由小到大排序
        providerDTOList = providerDTOList.stream().sorted(Comparator.comparing(ServeProviderDTO::getAcceptanceNum)).collect(Collectors.toList());

        ServeProviderDTO first = CollUtils.getFirst(providerDTOList);

        providerDTOList = providerDTOList.stream().filter(x->x.getAcceptanceNum().equals(first.getAcceptanceNum())).collect(Collectors.toList());

        return providerDTOList;
    }


}
