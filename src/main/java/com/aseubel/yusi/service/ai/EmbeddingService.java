package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.common.repochain.Processor;
import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.common.repochain.Result;
import com.aseubel.yusi.pojo.entity.Diary;

/**
 * @author Aseubel
 * @date 2025/5/7 下午1:34
 */
public class EmbeddingService implements Processor<Element> {

    @Override
    public Result<Element> process(Element data, int index, ProcessorChain<Element> chain) {
        if (data.getEventType() != EventType.DIARY_WRITE) {
            return chain.process(data, index);
        }
        Diary diary = (Diary) data.getData();
        // TODO: implement embedding logic here
        System.out.println("存储" + diary.getUserId() + "的日记的embedding");
        return Result.success(data);
    }
}
