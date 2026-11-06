package com.jzo2o.orders.dispatch.strategys;

import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;
import com.jzo2o.orders.dispatch.rules.IProcessRule;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 抽象策略类
 *
 * @Author mr.wu
 * @Date 2024-12-27 14:32
 */
@Slf4j
public  abstract class AbstractStategyImpl implements IProcessStrategy{

    private final IProcessRule processRule;

    public AbstractStategyImpl(){
        this.processRule = getRules();
    }


    abstract IProcessRule getRules();
    /**
     * 根据策略获取服务人员/机构
     *
     * @param serveProviderDTOList
     * @return
     */
    @Override
    public ServeProviderDTO getPrecedenceServeProvider(List<ServeProviderDTO> serveProviderDTOList) {
        List<ServeProviderDTO> dtoList = processRule.filter(serveProviderDTOList);
        int size = CollUtils.size(dtoList);
        ServeProviderDTO dto = null;
        if(size==0) {
            log.error("没有找到任何服务人员");
            return null;
        }
        if(size==1) {
            dto = dtoList.get(0);
            log.info("确定找到一个服务人员：{}" , dto);
            return dto;
        }
        int  i = (int)(Math.random()*size);
        log.info("最终获取的数量：{},随机数：{}", size, i );
        dto = dtoList.get(i);
        log.info("随机找到一个服务人员：{}" , dto);
        return dto;
    }
}
