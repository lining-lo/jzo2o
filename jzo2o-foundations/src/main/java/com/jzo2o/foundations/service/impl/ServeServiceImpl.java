package com.jzo2o.foundations.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.foundations.enums.FoundationStatusEnum;
import com.jzo2o.foundations.mapper.ServeMapper;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.domain.ServeItem;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.RegionResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IRegionService;
import com.jzo2o.foundations.service.IServeItemService;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
            if(ObjectUtils.isEmpty(item)) {
                throw new ForbiddenOperationException("服务项不存在不能添加");
            }

            //2.判断服务项是否未启动
            if(item.getActiveStatus()!= FoundationStatusEnum.ENABLE.getStatus()) {
                throw new ForbiddenOperationException("服务项未启动不能添加");
            }

            //3.判断服务项是否已添加过
            Integer count = this.lambdaQuery().eq(Serve::getRegionId, dto.getRegionId())
                    .eq(Serve::getServeItemId, dto.getServeItemId())
                    .count();
            if(count>0) {
                throw new ForbiddenOperationException("服务项不能重复添加");
            }

            //4.查询区域信息
            String cityCode = regionService.getById(dto.getRegionId()).getCityCode();

            //5.复制服务项为服务信息
            Serve serve = BeanUtils.copyBean(dto, Serve.class);
            serve.setCityCode(cityCode);

            //6.新增区域服务信息
            boolean result = this.save(serve);
            if(!result) {
                throw new DBException("添加服务项失败");
            }
        }
    }
}