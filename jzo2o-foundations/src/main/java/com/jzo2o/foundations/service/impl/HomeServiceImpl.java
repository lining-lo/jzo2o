package com.jzo2o.foundations.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.foundations.constants.RedisConstants;
import com.jzo2o.foundations.mapper.ServeMapper;
import com.jzo2o.foundations.model.domain.Region;
import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;
import com.jzo2o.foundations.model.dto.response.ServeSimpleResDTO;
import com.jzo2o.foundations.service.HomeService;
import com.jzo2o.foundations.service.IRegionService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class HomeServiceImpl implements HomeService {

    @Resource
    private IRegionService regionService;

    @Resource
    private ServeMapper serveMapper;


    /**
     * 首页服务列表
     * @param regionId
     * @return
     */
    @Override
    @Caching(cacheable = {
            // 如果结果集无值，说明缓存穿透，则应设置缓存为空值，有效期为30分钟
            @Cacheable(value = RedisConstants.CacheName.SERVE_ICON, key = "#regionId",
                    unless = "#result.size() != 0", cacheManager = RedisConstants.CacheManager.THIRTY_MINUTES),
            // 如果结果集有值，说明缓存命中，则应设置缓存为结果集，有效期为永久
            @Cacheable(value = RedisConstants.CacheName.SERVE_ICON, key = "#regionId",
                    unless = "#result.size() == 0", cacheManager = RedisConstants.CacheManager.FOREVER)
    })
    public List<ServeCategoryResDTO> firstPageServeList(Long regionId) {
        //1.检查区域状态是否启用
        Region region = regionService.getById(regionId);
        if(region==null || region.getActiveStatus()!=2){
            throw new ForbiddenOperationException("区域不存在或状态不对");
        }
        //2.根据城市编码查询所有服务类型和服务列表
        List<ServeCategoryResDTO> serveTypeAndServeList = serveMapper.getServeTypeAndServeList(regionId);
        if (ObjectUtil.isEmpty(serveTypeAndServeList)) {
            return Collections.emptyList();
        }
        //3.服务类型取前两个，每个类型下服务项取前4个
        Integer serveTypeSize = serveTypeAndServeList.size()>=2 ? 2:  serveTypeAndServeList.size();
        List<ServeCategoryResDTO> serveCategoryResDTOS = new ArrayList<>(serveTypeAndServeList.subList(0, serveTypeSize));
        serveCategoryResDTOS.forEach(serveType->{
            List<ServeSimpleResDTO> serveResDTOList = serveType.getServeResDTOList();
            Integer serveSize = serveResDTOList.size()>=4 ? 4 : serveResDTOList.size();

            List<ServeSimpleResDTO> serveSimpleResDTOS = new ArrayList<>(serveResDTOList.subList(0, serveSize));
            // serveResDTOList = serveResDTOList.subList(0,serveSize);
            serveType.setServeResDTOList(serveSimpleResDTOS);
        });
        return serveCategoryResDTOS;
    }
}