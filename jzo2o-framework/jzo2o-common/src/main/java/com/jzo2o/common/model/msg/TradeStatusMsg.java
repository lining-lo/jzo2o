package com.jzo2o.common.model.msg;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 交易状态消息
 *
 * @author itcast
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TradeStatusMsg {

    /**
     * 交易单号
     */
    private Long tradingOrderNo;

    /**
     * 业务系统标识
     */
    private String productAppId;
    /**
     * 订单号
     */
    private Long productOrderNo;

    /**
     * 第三方支付的交易号
     */
    private String transactionId;

    /**
     * 支付渠道
     */
    private String tradingChannel;

    /**
     * 退款单号
     */
    private Long refundNo;

    /**
     * 支付/退款 状态名称
     */
    private String statusName;

    /**
     * 支付/退款 状态编码
     */
    private Integer statusCode;

    /**
     * 扩展信息
     */
    private String info;

    /**
     * 创建时间
     */
    private Long created;

    /**
     * 支付成功时间
     */
    private LocalDateTime paySuccessTime;

    public String toJson() {
        return JSONUtil.toJsonStr(this);
    }
}
