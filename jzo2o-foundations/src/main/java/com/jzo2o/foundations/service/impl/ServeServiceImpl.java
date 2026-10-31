package com.jzo2o.foundations.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.foundations.constants.RedisConstants;
import com.jzo2o.foundations.enums.FoundationStatusEnum;
import com.jzo2o.foundations.enums.ServeHotEnum;
import com.jzo2o.foundations.mapper.*;
import com.jzo2o.foundations.model.domain.*;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.RegionResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IRegionService;
import com.jzo2o.foundations.service.IServeItemService;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务相关业务实现
 *
 */
@Service
public class ServeServiceImpl extends ServiceImpl<ServeMapper, Serve> implements IServeService {

    @Autowired
    private ServeMapper serveMapper;
    @Autowired
    private IServeItemService serveItemService;
    @Autowired
    private IRegionService regionService;
    @Autowired
    private RegionMapper regionMapper;
    @Autowired
    private ServeItemMapper serveItemMapper;
    @Autowired
    private ServeTypeMapper serveTypeMapper;
    @Autowired
    private ServeSyncMapper serveSyncMapper;

    /**
     * 新增服务同步数据
     *
     * @param serveId 服务id
     */
    private void addServeSync(Long serveId) {
        //服务信息
        Serve serve = baseMapper.selectById(serveId);
        //区域信息
        Region region = regionMapper.selectById(serve.getRegionId());
        //服务项信息
        ServeItem serveItem = serveItemMapper.selectById(serve.getServeItemId());
        //服务类型
        ServeType serveType = serveTypeMapper.selectById(serveItem.getServeTypeId());

        ServeSync serveSync = new ServeSync();
        serveSync.setServeTypeId(serveType.getId());
        serveSync.setServeTypeName(serveType.getName());
        serveSync.setServeTypeIcon(serveType.getServeTypeIcon());
        serveSync.setServeTypeImg(serveType.getImg());
        serveSync.setServeTypeSortNum(serveType.getSortNum());

        serveSync.setServeItemId(serveItem.getId());
        serveSync.setServeItemIcon(serveItem.getServeItemIcon());
        serveSync.setServeItemName(serveItem.getName());
        serveSync.setServeItemImg(serveItem.getImg());
        serveSync.setServeItemSortNum(serveItem.getSortNum());
        serveSync.setUnit(serveItem.getUnit());
        serveSync.setDetailImg(serveItem.getDetailImg());
        serveSync.setPrice(serve.getPrice());

        serveSync.setCityCode(region.getCityCode());
        serveSync.setId(serve.getId());
        serveSync.setIsHot(serve.getIsHot());
        serveSyncMapper.insert(serveSync);
    }


    /**
     * 分页查询服务列表
     *
     * @param servePageQueryReqDTO
     * @return
     */
    @Override
    public PageResult<ServeResDTO> pageList(ServePageQueryReqDTO servePageQueryReqDTO) {
        return PageHelperUtils.selectPage(
                servePageQueryReqDTO,
                () -> serveMapper.queryServeListByRegionId(servePageQueryReqDTO.getRegionId())
        );
    }

    @Transactional
    @Override
    public void updatePrice(Long id, BigDecimal price) {
        //1.判断服务是否存在
        Serve serve = this.getById(id);
        if (ObjectUtils.isEmpty(serve)) {
            throw new ForbiddenOperationException("服务不存在，禁止修改");
        }

        //2.更新服务价格
        boolean result = this.lambdaUpdate().set(Serve::getPrice, price).eq(Serve::getId, id).update();
        if (!result) {
            throw new DBException("修改服务价格失败");
        }
    }

    @Override
    @Transactional
    public void batchAdd(List<ServeUpsertReqDTO> dtoList) {
        for (ServeUpsertReqDTO dto : dtoList) {
            //1.判断服务项是否存在
            ServeItem item = serveItemService.getById(dto.getServeItemId());
            if (ObjectUtils.isEmpty(item)) {
                throw new ForbiddenOperationException("服务项不存在不能添加");
            }

            //2.判断服务项是否未启动
            if (item.getActiveStatus() != FoundationStatusEnum.ENABLE.getStatus()) {
                throw new ForbiddenOperationException("服务项未启动不能添加");
            }

            //3.判断服务项是否已添加过
            Integer count = this.lambdaQuery().eq(Serve::getRegionId, dto.getRegionId())
                    .eq(Serve::getServeItemId, dto.getServeItemId())
                    .count();
            if (count > 0) {
                throw new ForbiddenOperationException("服务项不能重复添加");
            }

            //4.查询区域信息
            String cityCode = regionService.getById(dto.getRegionId()).getCityCode();

            //5.复制服务项为服务信息
            Serve serve = BeanUtils.copyBean(dto, Serve.class);
            serve.setCityCode(cityCode);

            //6.新增区域服务信息
            boolean result = this.save(serve);
            if (!result) {
                throw new DBException("添加服务项失败");
            }
        }
    }

    /**
     * 上架
     *
     * @param id 服务id
     */
    @Transactional
    @Override
    @CachePut(value = RedisConstants.CacheName.SERVE, key = "#id", cacheManager = RedisConstants.CacheManager.ONE_DAY)
    public Serve onSale(Long id) {
        //1.判断服务是否存在
        Serve serve = this.getById(id);
        if (ObjectUtils.isEmpty(serve)) {
            throw new ForbiddenOperationException("服务不存在，禁止上架");
        }

        //2.判断是否有区域
        if (serve.getRegionId() == null) {
            throw new ForbiddenOperationException("区域未选，禁止上架");
        }

        //3.判断服务是否上架过
        if (serve.getSaleStatus() == FoundationStatusEnum.ENABLE.getStatus()) {
            throw new ForbiddenOperationException("不要重复上架");
        }

        //4.判断服务项是否存在
        ServeItem item = serveItemService.getById(serve.getServeItemId());
        if (ObjectUtils.isEmpty(item)) {
            throw new ForbiddenOperationException("服务项不存在，禁止上架");
        }

        //5.判断服务项是否已启动
        if (item.getActiveStatus() != FoundationStatusEnum.ENABLE.getStatus()) {
            throw new ForbiddenOperationException("服务项未启用，禁止上架");
        }

        //6.修改状态为已上架
        boolean result = this.lambdaUpdate().set(Serve::getSaleStatus, FoundationStatusEnum.ENABLE.getStatus()).eq(Serve::getId, id).update();
        if (!result) {
            throw new DBException("服务上架失败");
        }

        //添加同步表
        addServeSync(id);

        return baseMapper.selectById(id);
    }

    /**
     * 区域服务下架
     *
     * @param id
     */
    @Override
    @CacheEvict(value = RedisConstants.CacheName.SERVE, key = "#id")
    public Serve offSale(Long id) {
        //1.判断服务是否存在
        Serve serve = this.getById(id);
        if (ObjectUtils.isEmpty(serve)) {
            throw new ForbiddenOperationException("服务不存在，禁止下架");
        }

        //2.校验状态：不是上架状态，不能执行下架
        if (!serve.getSaleStatus().equals(FoundationStatusEnum.ENABLE.getStatus())) {
            throw new ForbiddenOperationException("服务不是上架状态，禁止重复下架");
        }

        //3.修改状态为已下架
        boolean result = this.lambdaUpdate()
                .set(Serve::getSaleStatus, FoundationStatusEnum.DISABLE.getStatus())
                .eq(Serve::getId, id)
                .update();
        if (!result) {
            throw new DBException("服务下架失败");
        }

        // 删除同步表的数据
        serveSyncMapper.deleteById(id);

        return baseMapper.selectById(id);
    }

    /**
     * 设置热门
     *
     * @param id
     */
    @Override
    public void onHot(Long id) {
        //1.判断服务是否存在
        Serve serve = this.getById(id);
        if (ObjectUtils.isEmpty(serve)) {
            throw new ForbiddenOperationException("服务不存在，操作失败");
        }

        //2.判断是否有区域
        if (serve.getRegionId() == null) {
            throw new ForbiddenOperationException("区域未选，操作失败");
        }

        // 判断是否已经是热门
        if (ServeHotEnum.HOT.equals(serve.getIsHot())) {
            throw new ForbiddenOperationException("该服务已经是热门，无需重复设置");
        }

        //3.判断服务是否上架
        if (!FoundationStatusEnum.ENABLE.equals(serve.getSaleStatus())) {
            throw new ForbiddenOperationException("服务未上架，操作失败");
        }

        //4.判断服务项是否存在
        ServeItem item = serveItemService.getById(serve.getServeItemId());
        if (ObjectUtils.isEmpty(item)) {
            throw new ForbiddenOperationException("服务项不存在，操作失败");
        }

        //5.判断服务项是否已启用
        if (!FoundationStatusEnum.ENABLE.equals(item.getActiveStatus())) {
            throw new ForbiddenOperationException("服务项未启用，操作失败");
        }

        //6.修改状态为热门 + 设置热门时间
        boolean result = this.lambdaUpdate()
                .set(Serve::getIsHot, ServeHotEnum.HOT.getStatus())
                .set(Serve::getHotTimeStamp, System.currentTimeMillis())
                .eq(Serve::getId, id)
                .update();
        if (!result) {
            throw new DBException("设置热门失败");
        }
    }

    /**
     * 取消热门
     *
     * @param id
     */
    @Override
    public void offHot(Long id) {
        //1.判断服务是否存在
        Serve serve = this.getById(id);
        if (ObjectUtils.isEmpty(serve)) {
            throw new ForbiddenOperationException("服务不存在，操作失败");
        }

        //2.判断是否有区域
        if (serve.getRegionId() == null) {
            throw new ForbiddenOperationException("区域未选，操作失败");
        }

        // 判断是否已经是取消热门
        if (ServeHotEnum.NOT_HOT.equals(serve.getIsHot())) {
            throw new ForbiddenOperationException("该服务已经不是热门，无需重复设置");
        }

        //3.判断服务是否上架
        if (!FoundationStatusEnum.ENABLE.equals(serve.getSaleStatus())) {
            throw new ForbiddenOperationException("服务未上架，操作失败");
        }

        //4.判断服务项是否存在
        ServeItem item = serveItemService.getById(serve.getServeItemId());
        if (ObjectUtils.isEmpty(item)) {
            throw new ForbiddenOperationException("服务项不存在，操作失败");
        }

        //5.判断服务项是否已启用
        if (!FoundationStatusEnum.ENABLE.equals(item.getActiveStatus())) {
            throw new ForbiddenOperationException("服务项未启用，操作失败");
        }

        //6.修改状态为取消热门
        boolean result = this.lambdaUpdate()
                .set(Serve::getIsHot, ServeHotEnum.NOT_HOT.getStatus())
                .set(Serve::getHotTimeStamp, null)
                .eq(Serve::getId, id)
                .update();
        if (!result) {
            throw new DBException("设置热门失败");
        }
    }

}