package com.jzo2o.foundations.handler;

import com.jzo2o.api.foundations.dto.response.RegionSimpleResDTO;
import com.jzo2o.foundations.constants.RedisConstants;
import com.jzo2o.foundations.service.HomeService;
import com.jzo2o.foundations.service.IRegionService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * springCache缓存同步任务
 *
 **/
@Slf4j
@Component
public class SpringCacheSyncHandler {

    @Resource
    private RedisTemplate<String,String> redisTemplate;

    @Resource
    private IRegionService regionService;

    @Resource
    private HomeService homeService;


    /**
     * 已启用区域缓存更新
     * 每日凌晨1点执行
     */
    @XxlJob(value = "activeRegionCacheSync")
    public void activeRegionCacheSync() {
        log.info(">>>>>>>>开始进行缓存同步，更新已启用区域");
        //1.清理缓存
        String key = RedisConstants.CacheName.JZ_CACHE + "::ACTIVE_REGIONS";
        redisTemplate.delete(key);

        //2.刷新缓存
        regionService.queryActiveRegionListCache();
        log.info(">>>>>>>>更新已启用区域完成");
    }

    /**
     * 每日刷新开通区域列表缓存
     */
    @XxlJob("activeRegionCacheSync")
    public void updateActiveRegionsCache(){
        log.info(">>>>>>>>>>>>>>>>>>>活动区域列表缓存更新任务开始执行>>>>>>>>>>>>>>>>>>>>>>>>");
        String redisKey = RedisConstants.CacheName.JZ_CACHE + "::" + "ACTIVE_REGIONS";

        //1.删除原有缓存数据
        redisTemplate.unlink(redisKey);

        //2.添加新的缓存数据
        List<RegionSimpleResDTO> regionList = regionService.queryActiveRegionListCache();

        //3.遍历区域列表
        for (RegionSimpleResDTO region : regionList) {
            String redisServeKey = RedisConstants.CacheName.SERVE_ICON + "::" + region.getId();

            //4.删除原有缓存数据
            redisTemplate.unlink(redisServeKey);

            //5.按照区域缓存服务列表
            homeService.firstPageServeList(region.getId());
        }

        log.info(">>>>>>>>>>>>>>>>>>>活动区域列表缓存更新任务执行完毕>>>>>>>>>>>>>>>>>>>>>>>>");
    }
}