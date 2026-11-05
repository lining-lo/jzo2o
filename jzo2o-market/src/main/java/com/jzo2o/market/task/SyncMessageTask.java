package com.jzo2o.market.task;

import com.jzo2o.api.customer.CommonUserApi;
import com.jzo2o.api.customer.dto.response.CommonUserResDTO;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.market.constants.RedisConstants;
import com.jzo2o.market.enums.CouponStatusEnum;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.redis.model.SyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 同步数据的异步处理类
 */
@Component
@Slf4j
public class SyncMessageTask {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IActivityService activityService;

    @Autowired
    private CommonUserApi commonUserApi;

    @Autowired
    private ICouponService couponService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 批量获取缓存同步数据
     * 将同步数据同步到DB
     * @param index 索引号的值，就是 活动id%10的结果
     * @return
     */
    @Async("taskExecutor")
    public void  batchHandeSyncMessage(int index){
        //执行任务
        log.info("{}执行任务:{}",Thread.currentThread().getName(), index);
        String redisKey = String.format(RedisConstants.RedisKey.COUPON_SEIZE_SYNC_QUEUE_NAME, index);

        //基于队列名获取锁实例
        RLock lock = redissonClient.getLock("LOCK:" + redisKey);
        //对队列名尝试加锁
        List<SyncMessage<Object>> syncMessageList = null;
        try {
            boolean result = lock.tryLock(3, 30, TimeUnit.SECONDS);
            //加锁失败，拒绝执行业务
            if(!result) {
                log.error("线程：{}加锁失败，" , Thread.currentThread().getName());
            } else {
                log.info("线程：{}加锁成功，" , Thread.currentThread().getName());
                //加锁成功可执行业务
                syncMessageList = this.batchGetSyncMessage(redisKey);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if(CollUtils.isNotEmpty(syncMessageList)) {
                int size = syncMessageList.size();
                syncMessageList.forEach(x->log.info("同步队列[{}]，同步数据数量{}，同步完毕", redisKey, size));

                SyncMessageTask owner = applicationContext.getBean(SyncMessageTask.class);
                owner.syncMessageToDB(syncMessageList);

                // 从hash表中删除数据
                syncMessageList.forEach(x->{
                    redisTemplate.opsForHash()
                            .delete(redisKey, x.getKey());
                });

                // 最后释放锁
                if(lock!=null) {
                    lock.unlock();
                    log.info("线程：{}释放锁成功，" , Thread.currentThread().getName());
                }
            }
        }
    }

    /**
     * 从同步数据队列中批量取出数据
     * @param redisKey
     */
    public List<SyncMessage<Object>> batchGetSyncMessage(String redisKey) {

        //通过游标方式批量获取Hash中的游标对象
        Cursor<Map.Entry<String,Object>> cursor = null;

        List<SyncMessage<Object>> syncMessageList = null;
        try {
            cursor = redisTemplate.opsForHash().scan(redisKey, ScanOptions.scanOptions().count(10).build());

            //解析游标对象中的数据
            syncMessageList = cursor.stream().map(x -> SyncMessage.builder()
                    .key(x.getKey().toString())
                    .value(x.getValue())
                    .build()
            ).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //关闭游标对象
            if(cursor!=null) {
                cursor.close();
            }
        }
        return syncMessageList;
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncMessageToDB(List<SyncMessage<Object>> syncMessageList) {
        syncMessageList.forEach(x->{
            Long userId = Long.valueOf(x.getKey()); //用户id
            Long activityId = Long.valueOf(x.getValue().toString()); //活动id

            //根据用户ID查询用户信息
            CommonUserResDTO user = commonUserApi.findById(userId);
            if(ObjectUtils.isNull(user)) {
                throw new CommonException("用户不存在");
            }

            //根据活动ID查询活动信息
            Activity activity = activityService.getById(activityId);
            if(ObjectUtils.isNull(activity)){
                throw new CommonException("活动不存在");
            }

            Coupon coupon = new Coupon();
            //设置用户信息
            coupon.setUserId(userId);
            coupon.setUserName(user.getNickname());
            coupon.setUserPhone(user.getPhone());

            //设置活动信息
            coupon.setActivityId(activityId);
            coupon.setType(activity.getType());
            coupon.setDiscountRate(activity.getDiscountRate());
            coupon.setDiscountAmount(activity.getDiscountAmount());
            coupon.setAmountCondition(activity.getAmountCondition());
            coupon.setValidityTime(DateUtils.now().plusDays(activity.getValidityDays()));
            coupon.setName(activity.getName());
            coupon.setStatus(CouponStatusEnum.NO_USE.getStatus());


            //新增优惠券
            boolean resultForCoupon = couponService.save(coupon);
            if(!resultForCoupon) {
                throw new DBException("优惠券新增失败");
            }

            //扣减库存
            boolean resultForActivity = activityService.decrStockNum(activityId);
            if(!resultForActivity) {
                throw new DBException("扣减活动库存失败");
            }
        });
    }
}