package com.aseubel.yusi.common.utils;

import com.aseubel.yusi.redis.service.IRedisService;
import com.github.houbb.sensitive.word.core.SensitiveWordHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SensitiveWordUtils {

    private final IRedisService redisService;

    private static final String VIOLATION_COUNT_KEY = "yusi:violation:count:%s";

    private static final long VIOLATION_EXPIRE_TIME = 12L * 60 * 60 * 1000;

    private static final List<String> FIRST_VIOLATION_RESPONSES = List.of(
            "抱歉哦，小予不能回答你这句话，说点别的吧",
            "这句话有点敏感呢，我们聊点别的吧",
            "小予暂时不能回答这个问题，换一个吧"
    );

    private static final List<String> SECOND_VIOLATION_RESPONSES = List.of(
            "哎呀，这句话不太合适呢~再这样下去小予要生气啦！",
            "小予已经悄悄提醒过你两次啦，下次可要小心哦~"
    );

    private static final List<String> THIRD_VIOLATION_RESPONSES = List.of(
            "真的要生气啦！这已经是第三次了哦~",
            "小予已经在生气的边缘徘徊了，再这样下去真的要不理你啦！"
    );

    private static final List<String> FOURTH_VIOLATION_RESPONSES = List.of(
            "呜...小予要生气一会儿了，不想理你啦！",
            "你太让小予伤心了...我要去冷静一下！",
            "气鼓鼓！小予需要一个人静静..."
    );

    private static final Random RANDOM = new Random();

    public String checkAndHandleViolation(String userId, String message) {
        if (!contains(message)) {
            return null;
        }

        Long violationCount = getViolationCount(userId);

        if (violationCount == null) {
            violationCount = 1L;
            redisService.setValue(String.format(VIOLATION_COUNT_KEY, userId), violationCount, VIOLATION_EXPIRE_TIME);
        } else {
            violationCount++;
            redisService.setValue(String.format(VIOLATION_COUNT_KEY, userId), violationCount, VIOLATION_EXPIRE_TIME);
        }

        log.warn("用户 {} 发送敏感词，违规次数：{}", userId, violationCount);

        if (violationCount == 1) {
            return getRandomResponse(FIRST_VIOLATION_RESPONSES);
        } else if (violationCount == 2) {
            return getRandomResponse(SECOND_VIOLATION_RESPONSES);
        } else if (violationCount == 3) {
            return getRandomResponse(THIRD_VIOLATION_RESPONSES);
        } else {
            int hours = 1 + RANDOM.nextInt(3);
            String restoreTimeMsg = String.format("大约 %d 小时后小予心情就会好起来啦", hours);
            return getRandomResponse(FOURTH_VIOLATION_RESPONSES) + " " + restoreTimeMsg;
        }
    }

    public boolean contains(String message) {
        return SensitiveWordHelper.contains(message);
    }

    public Long getViolationCount(String userId) {
        String key = String.format(VIOLATION_COUNT_KEY, userId);
        Object value = redisService.getValue(key);
        return value instanceof Long ? (Long) value : null;
    }

    public void clearViolationCount(String userId) {
        String key = String.format(VIOLATION_COUNT_KEY, userId);
        redisService.remove(key);
    }

    private String getRandomResponse(List<String> responses) {
        return responses.get(RANDOM.nextInt(responses.size()));
    }
}
