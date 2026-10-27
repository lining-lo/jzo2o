package com.jzo2o.foundations.controller.operation;

import com.jzo2o.common.model.PageResult;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IServeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 运营端 - 服务相关接口
 *
 */
@Validated
@RestController("operationServeController")
@RequestMapping("/operation/serve")
@Api(tags = "运营端 - 服务相关接口")
public class ServeController {

    @Resource
    private IServeService serveService;

    @GetMapping("/page")
    @ApiOperation("分页查询服务列表")
    public PageResult<ServeResDTO> page(ServePageQueryReqDTO servePageQueryReqDTO) {
        return serveService.pageList(servePageQueryReqDTO);
    }
}