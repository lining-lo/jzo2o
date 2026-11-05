package com.jzo2o.market.service.impl;

import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.*;
import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.market.enums.ActivityStatusEnum;
import com.jzo2o.market.enums.CouponStatusEnum;
import com.jzo2o.market.mapper.CouponMapper;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.model.domain.CouponWriteOff;
import com.jzo2o.market.model.dto.request.CouponOperationPageQueryReqDTO;
import com.jzo2o.market.model.dto.request.SeizeCouponReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.model.dto.response.CouponInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.market.service.ICouponUseBackService;
import com.jzo2o.market.service.ICouponWriteOffService;
import com.jzo2o.market.utils.CouponUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.mysql.utils.PageUtils;
import com.jzo2o.redis.utils.RedisSyncQueueUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.jzo2o.common.constants.ErrorInfo.Code.SEIZE_COUPON_FAILD;
import static com.jzo2o.market.constants.RedisConstants.RedisKey.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-09-16
 */
@Service
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    @Resource(name = "seizeCouponScript")
    private DefaultRedisScript<String> seizeCouponScript;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private IActivityService activityService;

    @Resource
    private ICouponUseBackService couponUseBackService;

    @Resource
    private ICouponWriteOffService couponWriteOffService;

    @Override
    public PageResult<CouponInfoResDTO> queryForPageOfOperation(CouponOperationPageQueryReqDTO couponOperationPageQueryReqDTO) {
        // 1.数据校验
        if (ObjectUtils.isNull(couponOperationPageQueryReqDTO.getActivityId())) {
            throw new BadRequestException("请指定活动");
        }
        // 2.数据查询
        // 分页 排序
        Page<Coupon> couponQueryPage = PageUtils.parsePageQuery(couponOperationPageQueryReqDTO, Coupon.class);
        // 查询条件
        LambdaQueryWrapper<Coupon> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Coupon::getActivityId, couponOperationPageQueryReqDTO.getActivityId());
        // 查询数据
        Page<Coupon> couponPage = baseMapper.selectPage(couponQueryPage, lambdaQueryWrapper);

        // 3.数据转化，并返回
        return PageUtils.toPage(couponPage, CouponInfoResDTO.class);
    }

    @Override
    public List<CouponInfoResDTO> queryForList(Long lastId, Long userId, Integer status) {

        // 1.校验
        if (status > 3 || status < 1) {
            throw new BadRequestException("请求状态不存在");
        }
        // 2.查询准备
        LambdaQueryWrapper<Coupon> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 查询条件
        lambdaQueryWrapper.eq(Coupon::getStatus, status)
                .eq(Coupon::getUserId, userId)
                .lt(ObjectUtils.isNotNull(lastId), Coupon::getId, lastId);
        // 查询字段
        lambdaQueryWrapper.select(Coupon::getId);
        // 排序
        lambdaQueryWrapper.orderByDesc(Coupon::getId);
        // 查询条数限制
        lambdaQueryWrapper.last(" limit 10 ");
        // 3.查询数据(数据中只含id)
        List<Coupon> couponsOnlyId = baseMapper.selectList(lambdaQueryWrapper);
        //判空
        if (CollUtils.isEmpty(couponsOnlyId)) {
            return new ArrayList<>();
        }

        // 4.获取数据且数据转换
        // 优惠id列表
        List<Long> ids = couponsOnlyId.stream()
                .map(Coupon::getId)
                .collect(Collectors.toList());
        // 获取优惠券数据
        List<Coupon> coupons = baseMapper.selectBatchIds(ids);
        // 数据转换
        return BeanUtils.copyToList(coupons, CouponInfoResDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long activityId) {
        lambdaUpdate()
                .set(Coupon::getStatus, CouponStatusEnum.VOIDED.getStatus())
                .eq(Coupon::getActivityId, activityId)
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .update();
    }

    @Override
    public Integer countReceiveNumByActivityId(Long activityId) {
        return lambdaQuery().eq(Coupon::getActivityId, activityId)
                .count();
    }

    @Override
    public void processExpireCoupon() {
        lambdaUpdate()
                .set(Coupon::getStatus, CouponStatusEnum.INVALID.getStatus())
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .le(Coupon::getValidityTime, DateUtils.now())
                .update();
    }

    /**
     * 抢券 （避免直接操作数据库，基于缓存和Lua）
     *
     * @param id
     */
    @Override
    public void seizeCoupon(Long id) {
        //1. 查询优惠券活动，判断是否存在
        ActivityInfoResDTO activity = getActivityFromCache(id);
        if(ObjectUtils.isNull(activity)) {
            throw new CommonException("优惠券活动不存在");
        }

        //2.判断优惠券的状态是否有效（非发放中都不行）
        Integer status = activityService.getCurrentRealStatus(activity.getStatus(), activity.getDistributeStartTime(), activity.getDistributeEndTime());
        if(ObjectUtils.notEqual(status, ActivityStatusEnum.DISTRIBUTING.getStatus())) {
            throw new CommonException("活动非法状态");
        }

        //3. 判断优惠券活动的时间段是否有效（未开始或已结束都不行）
        if(DateUtils.now().isBefore(activity.getDistributeStartTime()) || DateUtils.now().isAfter(activity.getDistributeEndTime())) {
            throw new CommonException("非法时间段");
        }

        //4. 准备Lua脚本的key和argv
        Long activityId = id;//ARGV[1] 活动id
        Long userId = UserContext.currentUser().getId(); //ARGV[2] 用户id
        List keys = Arrays.asList(
                //KEYS[1] 抢券同步队列 RedisKey
                String.format(RedisConstants.RedisKey.COUPON_SEIZE_SYNC_QUEUE_NAME, activityId%10),
                //KEYS[2] 优惠券活动库存 RedisKey
                String.format(RedisConstants.RedisKey.COUPON_RESOURCE_STOCK, activityId%10),
                //KEYS[3] 抢券成功队列 RedisKey
                String.format(RedisConstants.RedisKey.COUPON_SEIZE_LIST, activityId, activityId%10)
        );

        //5. 调用lua脚本执行抢券处理
        Object result = redisTemplate.execute(seizeCouponScript, keys, activityId, userId);

        //6. 根据Lua脚本执行结果进行处理
        if(ObjectUtils.isNull(result)) {
            throw new CommonException("抢券失败");
        }
        Long code = Long.valueOf(result.toString());
        if(code>0) {
            return; //抢券成功
        }
        if(code==-1) {
            throw new CommonException("限领一张");
        }

        if(code==-2 || code==-4) {
            throw new CommonException("已抢光");
        }

        if(code==-3 || code==-5) {
            throw new CommonException("抢券失败");
        }

    }

    /**
     * 根据活动id从缓存中找到对应活动信息
     * @param id
     * @return
     */
    private ActivityInfoResDTO getActivityFromCache(Long id) {
        String activityRedisKey = RedisConstants.RedisKey.ACTIVITY_CACHE_LIST;
        Object activityObject = redisTemplate.opsForValue().get(activityRedisKey);
        if(ObjectUtils.isNull(activityObject)) {
            return null;
        }
        List<ActivityInfoResDTO> activityInfoResDTOList = JsonUtils.toList(activityObject.toString(), ActivityInfoResDTO.class);
        return activityInfoResDTOList.stream().filter(x->ObjectUtils.equals(x.getId(),id)).findFirst().orElse(null);
    }

    /**
     * 获取可用优惠券列表
     * @param totalAmount
     * @return
     */
    @Override
    public List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount) {
        //1.获取满足条件的优惠券列表
        //条件：用户id、未使用、未过期、满减金额（门槛）<=总金额
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getUserId, UserContext.currentUserId())
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())
                .gt(Coupon::getValidityTime, DateUtils.now())
                .le(Coupon::getAmountCondition, totalAmount)
                .list();
        if(CollUtils.isEmpty(couponList)){
            return CollUtils.emptyList();
        }


        //2.对优惠券列表进一步优化
        //优化：计算优惠金额、筛选合适的优惠券、转换类型、按照优惠金额倒序
        List<AvailableCouponsResDTO> availableCouponsResDTOList = couponList.stream()
                .peek(x -> x.setDiscountAmount(CouponUtils.calDiscountAmount(x, totalAmount)))
                .filter(x -> x.getDiscountAmount().compareTo(new BigDecimal("0")) > 0 && x.getDiscountAmount().compareTo(totalAmount) <= 0)
                .map(x -> BeanUtils.copyBean(x, AvailableCouponsResDTO.class))
                .sorted(Comparator.comparing(AvailableCouponsResDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
        return availableCouponsResDTOList;
    }
}
