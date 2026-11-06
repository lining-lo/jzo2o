package com.jzo2o.orders.dispatch.strategys;

import com.jzo2o.orders.dispatch.rules.IProcessRule;
import com.jzo2o.orders.dispatch.rules.impl.AcceptNumRule;
import com.jzo2o.orders.dispatch.rules.impl.ScoreRule;

/**
 * 最少接单优先策略：谁接单数少谁优先，如果接单数一样看评分，谁的分高谁优先
 *
 * @Author mr.wu
 * @Date 2024-12-27 14:38
 */
public class AcceptNumStrategyImpl extends AbstractStategyImpl{

    @Override
    IProcessRule getRules() {
        ScoreRule scoreRule = new ScoreRule(null);
        AcceptNumRule ruleChain = new AcceptNumRule(scoreRule);
        return ruleChain;
    }
}
