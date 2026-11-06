package com.jzo2o.orders.dispatch.strategys;

import com.jzo2o.orders.dispatch.rules.IProcessRule;
import com.jzo2o.orders.dispatch.rules.impl.AcceptNumRule;
import com.jzo2o.orders.dispatch.rules.impl.ScoreRule;

/**
 * 评分优先策略：谁评分高谁优先，评分相同看接单数，谁接单数少谁优先
 *
 * @Author mr.wu
 * @Date 2024-12-27 14:38
 */
public class ScoreStrategyImpl extends AbstractStategyImpl{

    @Override
    IProcessRule getRules() {
        AcceptNumRule acceptNumRule = new AcceptNumRule(null);
        ScoreRule ruleChain = new ScoreRule(acceptNumRule);
        return ruleChain;
    }
}
