package com.jzo2o.orders.manager.service;

import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.statemachine.OrderStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 订单状态机测试类
 *
 * @Author mr.wu
 * @Date 2024-12-17 9:52
 */
@SpringBootTest
public class OrderStateMachineTest {


    @Autowired
    private OrderStateMachine orderStateMachine;

    /**
     * 启动状态机测试
     * 针对具体的一条业务数据，仅能启动一次状态机
     */
    @Test
    public void testStartMachine(){
        orderStateMachine.start("1234567");
    }


    /**
     * 测试变更状态
     * 指定的事件中的初始状态需要与状态机state_persister表里最新状态一致
     */
    @Test
    public void testChanageStatus(){
        orderStateMachine.changeStatus("1234567", OrderStatusChangeEventEnum.CANCEL);
    }
}
