package com.jzo2o.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.*;
import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.market.constants.TabTypeConstants;
import com.jzo2o.market.enums.ActivityStatusEnum;
import com.jzo2o.market.mapper.ActivityMapper;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.dto.request.ActivityQueryForPageReqDTO;
import com.jzo2o.market.model.dto.request.ActivitySaveReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.market.service.ICouponWriteOffService;
import com.jzo2o.mysql.utils.PageUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.jzo2o.market.enums.ActivityStatusEnum.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-09-16
 */
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {
    private static final int MILLION = 1000000;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private ICouponService couponService;

    @Resource
    private ICouponWriteOffService couponWriteOffService;

    @Override
    public PageResult<ActivityInfoResDTO> queryForPage(ActivityQueryForPageReqDTO activityQueryForPageReqDTO) {
        LocalDateTime now = DateUtils.now();
        // 1.查询准备
        LambdaQueryWrapper<Activity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 查询条件
        lambdaQueryWrapper.eq(ObjectUtils.isNotNull(activityQueryForPageReqDTO.getId()), Activity::getId, activityQueryForPageReqDTO.getId())
                .like(StringUtils.isNotEmpty(activityQueryForPageReqDTO.getName()), Activity::getName, activityQueryForPageReqDTO.getName())
                .eq(ObjectUtils.isNotNull(activityQueryForPageReqDTO.getType()), Activity::getType, activityQueryForPageReqDTO.getType())
                .eq(ObjectUtils.isNotNull(activityQueryForPageReqDTO.getStatus()), Activity::getStatus, activityQueryForPageReqDTO.getStatus());

        // 排序
        lambdaQueryWrapper.orderByDesc(Activity::getId);
        // 分页
        Page<Activity> activityPage = new Page<>(activityQueryForPageReqDTO.getPageNo().intValue(), activityQueryForPageReqDTO.getPageSize().intValue());
        activityPage = baseMapper.selectPage(activityPage, lambdaQueryWrapper);
        return PageUtils.toPage(activityPage, ActivityInfoResDTO.class);
    }

    @Override
    public ActivityInfoResDTO queryById(Long id) {
        // 1.获取活动
        Activity activity = baseMapper.selectById(id);
        // 判空
        if (activity == null) {
            return new ActivityInfoResDTO();
        }
        // 2.数据转换，并返回信息
        ActivityInfoResDTO activityInfoResDTO = BeanUtils.toBean(activity, ActivityInfoResDTO.class);
        // 设置状态
//        activityInfoResDTO.setStatus(getStatus(activity.getDistributeStartTime(), activity.getDistributeEndTime(), activity.getStatus()));
        // 3.领取数量
//        Integer receiveNum = couponService.countReceiveNumByActivityId(activity.getId());
        Integer receiveNum = activity.getTotalNum()-activity.getStockNum();
        activityInfoResDTO.setReceiveNum(receiveNum);
        // 4.核销量
        Integer writeOffNum = couponWriteOffService.countByActivityId(id);
        activityInfoResDTO.setWriteOffNum(NumberUtils.null2Zero(writeOffNum));

        //
        return activityInfoResDTO;
    }

    @Override
    public void save(ActivitySaveReqDTO activitySaveReqDTO) {
        // 1.逻辑校验
        activitySaveReqDTO.check();
        // 2.活动数据组装
        // 转换
        Activity activity = BeanUtils.toBean(activitySaveReqDTO, Activity.class);
        // 状态
        activity.setStatus(NO_DISTRIBUTE.getStatus());
        //库存
        activity.setStockNum(activitySaveReqDTO.getTotalNum());
        if(activitySaveReqDTO.getId() == null) {
            activity.setId(IdUtils.getSnowflakeNextId());
        }
        //排序字段
//        long sortBy = DateUtils.toEpochMilli(activity.getDistributeStartTime()) * MILLION + activity.getId() % MILLION;
        // 3.保存
        saveOrUpdate(activity);
    }


    @Override
    public void updateStatus() {
        LocalDateTime now = DateUtils.now();
        // 1.更新已经进行中的状态
        lambdaUpdate()
                .set(Activity::getStatus, ActivityStatusEnum.DISTRIBUTING.getStatus())//更新活动状态为进行中
                .eq(Activity::getStatus, NO_DISTRIBUTE)//检索待生效的活动
                .le(Activity::getDistributeStartTime, now)//活动开始时间小于等于当前时间
                .gt(Activity::getDistributeEndTime,now)//活动结束时间大于当前时间
                .update();
        // 2.更新已经结束的
        lambdaUpdate()
                .set(Activity::getStatus, LOSE_EFFICACY.getStatus())//更新活动状态为已失效
                .in(Activity::getStatus, Arrays.asList(DISTRIBUTING.getStatus(), NO_DISTRIBUTE.getStatus()))//检索待生效及进行中的活动
                .lt(Activity::getDistributeEndTime, now)//活动结束时间小于当前时间
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void

    revoke(Long id) {
        // 1.活动作废
        boolean update = lambdaUpdate()
                .set(Activity::getStatus, ActivityStatusEnum.VOIDED.getStatus())
                .eq(Activity::getId, id)
                .in(Activity::getStatus, Arrays.asList(NO_DISTRIBUTE.getStatus(), DISTRIBUTING.getStatus()))
                .update();
        if(!update) {
            return;
        }
        // 2.未使用优惠券作废
        couponService.revoke(id);

    }

    /**
     * 活动缓存处理
     */
    @Override
    public void preHeat() {
        //1.查询活动列表（30天内进行中的和待开始的），结果根据活动开始时间倒序
        //select * from activity where status in (1,2)
        // and distribute_start_time<date_add(now(),interval 30 day)
        //  order by distribute_start_time desc

        List<Activity> activityList = this.lambdaQuery()
                .in(Activity::getStatus, NO_DISTRIBUTE.getStatus(), DISTRIBUTING.getStatus())
                .lt(Activity::getDistributeStartTime, DateUtils.now().plusDays(30))
                .orderByDesc(Activity::getDistributeStartTime)
                .list();
        if (CollUtils.isEmpty(activityList)) {
            activityList = new ArrayList<>();
        }

        //2.转为前端需要的对象列表
        List<ActivityInfoResDTO> activityInfoResDTOList = BeanUtils.copyToList(activityList, ActivityInfoResDTO.class);

        //3.将对象列表转为JSON字符串
        String activityListJSON = JsonUtils.toJsonStr(activityInfoResDTOList);

        //4.保存到Redis缓存中（永不过期）
        redisTemplate.opsForValue().set(RedisConstants.RedisKey.ACTIVITY_CACHE_LIST, activityListJSON);
    }

    /**
     * 查询活动列表（基于缓存，并实时同步活动状态）
     *
     * @param tabType
     * @return
     */
    @Override
    public List<ActivityInfoResDTO> queryForListFromCache(Integer tabType) {

        //1.从redis中查询活动列表JSON字符串
        Object activityObj = redisTemplate.opsForValue().get(RedisConstants.RedisKey.ACTIVITY_CACHE_LIST);
        if (ObjectUtils.isNull(activityObj)) {
            return CollUtils.emptyList();
        }

        //2.将JSON字符串转为对象列表
        List<ActivityInfoResDTO> activityList = JsonUtils.toList(activityObj.toString(), ActivityInfoResDTO.class);

        //3.遍历对象，筛选出用户要选的活动列表
        //用户所选类型对应的活动状态
        Integer selectStatus = ObjectUtils.equal(tabType, TabTypeConstants.SEIZING) ? DISTRIBUTING.getStatus() : NO_DISTRIBUTE.getStatus();
        //活动中活动当前最实时的状态（可通过原始状态和原始数据的开始时间、结束时间一起算）

        List<ActivityInfoResDTO> activityListSelect = activityList.stream()
                .filter(x -> ObjectUtils.equals(selectStatus, getCurrentRealStatus(x.getStatus(), x.getDistributeStartTime(), x.getDistributeEndTime())))
                .peek(x -> {
                    x.setRemainNum(x.getStockNum());//优惠券剩余数量等于优惠券库存数量
                    x.setStatus(selectStatus);
                })
                .collect(Collectors.toList());

        return activityListSelect;
    }


    /**
     * 获取活动实时状态
     *
     * @param originStatus
     * @param distributeStartTime
     * @param distributeEndTime
     * @return
     */
    public Integer getCurrentRealStatus(Integer originStatus, LocalDateTime distributeStartTime, LocalDateTime distributeEndTime) {

        if (ObjectUtils.equals(originStatus, NO_DISTRIBUTE.getStatus())
                && distributeStartTime.isBefore(DateUtils.now())
                && distributeEndTime.isAfter(DateUtils.now())
        ) {
            return DISTRIBUTING.getStatus(); //待开始状态的订单，时间已到，获取最新状态为发放中
        } else if (ObjectUtils.equals(originStatus, NO_DISTRIBUTE.getStatus())
                && distributeEndTime.isBefore(DateUtils.now())
        ) {
            return LOSE_EFFICACY.getStatus(); //待开始状态的订单，时间已过，获取最新状态为已失效
        } else if (ObjectUtils.equals(originStatus, DISTRIBUTING.getStatus())
                && distributeEndTime.isBefore(DateUtils.now())
        ) {
            return LOSE_EFFICACY.getStatus(); //进行中状态的订单，时间已过，获取最新状态为已失效
        }
        return originStatus;
    }
}
