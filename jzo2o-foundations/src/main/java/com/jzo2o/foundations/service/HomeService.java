package com.jzo2o.foundations.service;

import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;

import java.util.List;

public interface HomeService {

    /**
     * 首页服务列表
     * @param regionId
     * @return
     */
    List<ServeCategoryResDTO> firstPageServeList(Long regionId);
}