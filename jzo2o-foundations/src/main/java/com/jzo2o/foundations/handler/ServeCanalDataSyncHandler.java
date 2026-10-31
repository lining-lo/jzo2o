package com.jzo2o.foundations.handler;

import com.jzo2o.canal.listeners.AbstractCanalRabbitMqMsgListener;
import com.jzo2o.es.core.ElasticSearchTemplate;
import com.jzo2o.foundations.constants.IndexConstants;
import com.jzo2o.foundations.model.domain.ServeSync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * serve_sync表数据同步到ES程序（MQ监听器类）
 */
@Component
@Slf4j
public class ServeCanalDataSyncHandler extends AbstractCanalRabbitMqMsgListener<ServeSync> {

    @Resource
    private ElasticSearchTemplate elasticSearchTemplate;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "canal-mq-jzo2o-foundations"),
            exchange = @Exchange(name = "exchange.canal-jzo2o" ,type = ExchangeTypes.TOPIC),
            key = "canal-mq-jzo2o-foundations"
    ),
            concurrency = "1"
    )
    public void listenMsg(Message message) throws Exception {
        parseMsg(message);
    }


    @Override
    public void batchSave(List<ServeSync> data) {
        elasticSearchTemplate.opsForDoc().batchInsert(IndexConstants.SERVE, data);
        log.info("RabbitMQ监听器：serve_sync数据新增或更新到ES完毕");
    }

    @Override
    public void batchDelete(List<Long> ids) {
        elasticSearchTemplate.opsForDoc().batchDelete(IndexConstants.SERVE, ids);
        log.info("RabbitMQ监听器：serve_sync数据从ES中删除完毕");
    }
}