package com.jzo2o.orders.dispatch.rules.impl;

import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;
import com.jzo2o.orders.dispatch.rules.IProcessRule;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 按照距离匹配的规则处理器类
 *
 * @Author mr.wu
 * @Date 2024-12-27 10:56
 */
public class DistanceRule extends AbstractProcessRule{


    public DistanceRule(IProcessRule next) {
        super(next);
    }

    /**
     * 具体规则处理方法
     * @param providerDTOList
     * @return
     */
    public List<ServeProviderDTO> doFilter(List<ServeProviderDTO> providerDTOList){
        System.out.println("按照距离优先，选出服务人员");
        if(CollUtils.size(providerDTOList)<2) {
            return providerDTOList;
        }

        //根据距离由近及远排序
        providerDTOList = providerDTOList.stream().sorted(Comparator.comparing(ServeProviderDTO::getAcceptanceDistance)).collect(Collectors.toList());

        ServeProviderDTO first = CollUtils.getFirst(providerDTOList);

        providerDTOList = providerDTOList.stream().filter(x->x.getAcceptanceDistance().equals(first.getAcceptanceDistance())).collect(Collectors.toList());

        return providerDTOList;
    }


}
