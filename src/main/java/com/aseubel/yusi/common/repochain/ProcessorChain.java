package com.aseubel.yusi.common.repochain;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aseubel
 * @description 处理器链
 * @date 2025/4/28 上午12:40
 */
public class ProcessorChain<T> {

    // 保存处理节点
    private final List<Processor<T>> processorList = new ArrayList<>();

    // 动态扩展处理节点
    public ProcessorChain<T> addProcessor(Processor<T> processor) {
        processorList.add(processor);
        return this;
    }

    /**
     * 配置处理器时调用该方法，index不用改变直接传
     * @param data 要处理的数据
     * @param index 当前处理节点索引
     * @return 处理结果
     */
    public Result<T> process(T data, int index) {
        if(index == processorList.size()) {
            return Result.success(data);
        }
        Processor<T> processor = processorList.get(index);
        return processor.process(data, index + 1, this);
    }

    /**
     * 使用责任链开始处理物品
     * @param data 要处理的数据
     * @return 是否处理成功
     */
    public Result<T> process(T data) {
        return process(data, 0);
    }

}
