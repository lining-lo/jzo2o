package com.jzo2o.foundations.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.foundations.mapper.ServeMapper;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.response.RegionResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
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
}