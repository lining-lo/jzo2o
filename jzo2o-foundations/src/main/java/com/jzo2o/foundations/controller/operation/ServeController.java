package com.jzo2o.foundations.controller.operation;

import com.jzo2o.common.model.PageResult;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IServeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;

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

    @PutMapping("/{id}")
    @ApiOperation("区域服务价格修改")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "服务id", required = true, dataTypeClass = Long.class),
            @ApiImplicitParam(name = "price", value = "价格", required = true, dataTypeClass = BigDecimal.class)
    })
    public void update(@PathVariable("id") Long id,
                       @RequestParam("price") BigDecimal price) {
        serveService.updatePrice(id, price);
    }
}