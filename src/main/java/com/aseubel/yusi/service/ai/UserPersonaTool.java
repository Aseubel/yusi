package com.aseubel.yusi.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.service.user.UserPersonaService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户画像管理工具
 * 允许 AI 在对话过程中自动更新用户的高保真偏好信息。
 *
 * @author Aseubel
 * @date 2026/02/10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPersonaTool {

    private final UserPersonaService userPersonaService;

    @Tool(name = "updateUserPersona", value = """
            更新用户的长期画像或偏好设置。
            当用户明确表达某些长期有效的偏好、身份信息或要求时调用此工具。
            
            示例场景：
            - 用户说："叫我大卫就好" -> preferredName="大卫"
            - 用户说："我搬到上海了" -> location="上海"
            - 用户说："我最近迷上了胶片摄影" -> interests="胶片摄影"
            - 用户说："不用安慰我，陪着我就好" -> tone="倾听者，少说话"
            
            注意：只传需要更新的字段，不需要更新的字段传 null。
            """)
    public String updateUserPersona(
            @ToolMemoryId String memoryId,
            @P("用户希望被称呼的名字/昵称") String preferredName,
            @P("用户所在的城市或地区") String location,
            @P("用户的兴趣爱好/话题偏好") String interests,
            @P("用户偏好的对话语气（如温柔、傲娇、倾听）") String tone,
            @P("其他相处模式或自定义指令") String customInstructions) {
        
        String userId = memoryId;
        if (StrUtil.isBlank(userId)) {
            return "无法验证用户身份，操作失败。";
        }

        log.info("Updating user persona for user {}: name={}, loc={}, interests={}, tone={}, custom={}",
                userId, preferredName, location, interests, tone, customInstructions);

        UserPersona update = UserPersona.builder()
                .preferredName(preferredName)
                .location(location)
                .interests(interests)
                .tone(tone)
                .customInstructions(customInstructions)
                .build();

        userPersonaService.updateUserPersona(userId, update);

        return "用户画像已更新。这些偏好将在未来的对话中持续生效。";
    }
}
