package com.jzo2o.orders.dispatch.rules.impl;

import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;
import com.jzo2o.orders.dispatch.rules.IProcessRule;

import java.util.List;

/**
 * TODO
 *
 * @Author mr.wu
 * @Date 2024-12-27 11:35
 */
public abstract class AbstractProcessRule  implements IProcessRule {

    private IProcessRule next;

    public AbstractProcessRule(IProcessRule next){
        this.next = next;
    }

    abstract List<ServeProviderDTO> doFilter(List<ServeProviderDTO> providerDTOList);

    /**
     * 筛选出符合规则的服务人员或机构
     *
     * @param providerDTOList
     * @return
     */
    @Override
    public List<ServeProviderDTO> filter(List<ServeProviderDTO> providerDTOList) {
        List<ServeProviderDTO> serveProviderDTOList = this.doFilter(providerDTOList);
        if(CollUtils.size(serveProviderDTOList)>1 && next!=null) {
            return next.filter(serveProviderDTOList);
        }
        return serveProviderDTOList;
    }

    /**
     * 链中的下个一个规则处理类对象
     *
     * @return
     */
    @Override
    public IProcessRule next() {
        return null;
    }
}
