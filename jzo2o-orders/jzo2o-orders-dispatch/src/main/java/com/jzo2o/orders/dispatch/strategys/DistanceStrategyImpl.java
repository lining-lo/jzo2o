package com.jzo2o.orders.dispatch.strategys;

import com.jzo2o.orders.dispatch.rules.IProcessRule;
import com.jzo2o.orders.dispatch.rules.impl.AcceptNumRule;
import com.jzo2o.orders.dispatch.rules.impl.DistanceRule;

/**
 * 距离优先策略：谁距离近谁优先，距离相同看接单数，谁接单数少谁优先
 *
 * @Author mr.wu
 * @Date 2024-12-27 14:38
 */
public class DistanceStrategyImpl extends AbstractStategyImpl{

    @Override
    IProcessRule getRules() {
        AcceptNumRule acceptNumRule = new AcceptNumRule(null);
        DistanceRule ruleChain = new DistanceRule(acceptNumRule);
        return ruleChain;
    }
}
