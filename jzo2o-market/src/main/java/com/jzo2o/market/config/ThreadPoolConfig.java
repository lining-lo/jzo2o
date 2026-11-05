package com.jzo2o.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class ThreadPoolConfig {

    /**
     * 声明线程池
     */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor(){

        //实例化线程池对象
        ThreadPoolTaskExecutor threadPoolTaskExecutor=new ThreadPoolTaskExecutor();
        //设置核心线程数
        threadPoolTaskExecutor.setCorePoolSize(10);
        //设置任务等待队列的大小
        threadPoolTaskExecutor.setQueueCapacity(5);
        //设置最大线程数
        threadPoolTaskExecutor.setMaxPoolSize(30);
        //设置线程等待超时时间
        threadPoolTaskExecutor.setKeepAliveSeconds(120);
        //设置线程池内线程的名字前缀 -----阿里编码规约推荐---- 方便后期错误调试
        threadPoolTaskExecutor.setThreadNamePrefix("jzo2o_thread_");
        //设置任务拒绝策略
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        //初始化线程池
        threadPoolTaskExecutor.initialize();
        //返回线程池对象
        return threadPoolTaskExecutor;
    }
}