package com.jzo2o.market.service;

import com.jzo2o.market.task.SyncMessageTask;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootTest
@Slf4j
public class SyncThreadPoolTest {

    @Autowired
    private SyncMessageTask syncMessageTask;

    @Test
    public void testThreadPool() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            syncMessageTask.batchHandeSyncMessage(i);
        }
        //主线程休眠一定的时间防止程序结束
        Thread.sleep(1000);
    }
 }