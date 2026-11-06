package com.jzo2o.orders.dispatch.test.strategys;

import com.jzo2o.orders.dispatch.model.dto.ServeProviderDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 按照策略，测试责任链效果
 *
 * @Author mr.wu
 * @Date 2024-12-27 11:01
 */
public class StrategyTest {


    /**
     *  距离优先：谁距离近谁优先，距离相同看接单数，谁接单数少谁优先
     *   评分优先：谁评分高谁优先，评分相同看接单数，谁接单数少谁优先
     *   最少接单优先：谁接单数少谁优先，如果接单数一样看评分，谁的分高谁优先
     */

    @Test
    public void testRule(){
        //准备一些服务人员
        List<ServeProviderDTO> serveProviderDTOList = Arrays.asList(
                //分数最低的2个人
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(60).acceptanceDistance(30).acceptanceNum(3).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(60).acceptanceDistance(30).acceptanceNum(3).build(),

                //距离最近的2个人
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(70).acceptanceDistance(10).acceptanceNum(9).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(10).acceptanceDistance(10).acceptanceNum(9).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(20).acceptanceDistance(10).acceptanceNum(11).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(20).acceptanceDistance(10).acceptanceNum(5).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(30).acceptanceDistance(10).acceptanceNum(14).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(75).acceptanceDistance(20).acceptanceNum(13).build(),

                //接单数最少的4个人
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(80).acceptanceDistance(30).acceptanceNum(1).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(100).acceptanceDistance(80).acceptanceNum(1).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(100).acceptanceDistance(70).acceptanceNum(1).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(100).acceptanceDistance(60).acceptanceNum(1).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(95).acceptanceDistance(30).acceptanceNum(1).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(100).acceptanceDistance(60).acceptanceNum(2).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(100).acceptanceDistance(40).acceptanceNum(5).build(),
                ServeProviderDTO.builder().id(1L).serveProviderType(2).evaluationScore(90).acceptanceDistance(50).acceptanceNum(2).build()
        );

        //测试第一种策略：距离优先 - 谁距离近谁优先，距离相同看接单数，谁接单数少谁优先
        

        //测试第二种策略：评分优先 - 谁评分高谁优先，评分相同看接单数，谁接单数少谁优先
      

        //测试第三种策略：最少接单优先 - 谁接单数少谁优先，如果接单数一样看评分，谁的分高谁优先

    }



}
