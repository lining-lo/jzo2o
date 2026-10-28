package com.jzo2o.foundations.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 服务相关业务接口
 *
 */
public interface IServeService extends IService<Serve> {
    /**
     * 分页查询服务列表
     * @param servePageQueryReqDTO
     * @return
     */
    PageResult<ServeResDTO> pageList(ServePageQueryReqDTO servePageQueryReqDTO);

    /**
     * 修改服务价格
     * @param id
     * @param price
     */
    void updatePrice(Long id, BigDecimal price);

    /**
     * 批量新增
     *
     * @param serveUpsertReqDTOList 批量新增数据
     */
    void batchAdd(List<ServeUpsertReqDTO> serveUpsertReqDTOList);
}