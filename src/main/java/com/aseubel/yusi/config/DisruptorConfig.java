package com.aseubel.yusi.config;

import com.aseubel.yusi.common.disruptor.DisruptorProducer;
import com.aseubel.yusi.common.disruptor.DisruptorStarter;
import com.aseubel.yusi.common.disruptor.Element;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.dsl.ProducerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

/**
 * @author Aseubel
 * @date 2025/5/7 下午1:14
 */
@Configuration
public class DisruptorConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "disruptorProducer")
    public DisruptorProducer disruptor() {
        // 生产者的线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "simpleThread");
            }
        };

        // RingBuffer生产工厂,初始化RingBuffer的时候使用
        EventFactory<Element> factory = new EventFactory<Element>() {
            @Override
            public Element newInstance() {
                return new Element();
            }
        };

        // 阻塞策略
        BlockingWaitStrategy strategy = new BlockingWaitStrategy();

        // 指定RingBuffer的大小
        int bufferSize = 1024;

        // 创建disruptor，采用单生产者模式
        DisruptorProducer disruptor = new DisruptorProducer(factory, bufferSize, threadFactory, ProducerType.SINGLE, strategy);

        // 指定启动责任链的处理器
        disruptor.handleEventsWith(applicationContext.getBean(DisruptorStarter.class));

        disruptor.start();
        return disruptor;
    }
}
