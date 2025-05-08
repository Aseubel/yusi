package com.aseubel.yusi.common.disruptor;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.ThreadFactory;

/**
 * @author Aseubel
 * @date 2025/5/7 下午5:05
 */
public class DisruptorProducer extends Disruptor<Element> {

    public void publish(Object data, EventType eventType) {
        long sequence = super.getRingBuffer().next();
        Element event = super.getRingBuffer().get(sequence);
        event.setData(data);
        event.setEventType(eventType);
        super.getRingBuffer().publish(sequence);
    }

    public DisruptorProducer(EventFactory<Element> eventFactory, int ringBufferSize, ThreadFactory threadFactory, ProducerType producerType, WaitStrategy waitStrategy) {
        super(eventFactory, ringBufferSize, threadFactory, producerType, waitStrategy);
    }

    public DisruptorProducer(EventFactory<Element> eventFactory, int ringBufferSize, ThreadFactory threadFactory) {
        super(eventFactory, ringBufferSize, threadFactory);
    }
}
