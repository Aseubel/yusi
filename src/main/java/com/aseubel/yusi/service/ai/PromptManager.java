package com.aseubel.yusi.service.ai;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.event.PromptUpdatedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词统一管理与降级缓存管理器
 * 提供基于事件的热更新能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptManager {

    private final PromptService promptService;

    // Cache structure: Key -> Prompt template
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("====== 开始初始化全局提示词管理器 (PromptManager) ======");
        for (PromptKey key : PromptKey.values()) {
            loadPrompt(key.getKey());
        }
        log.info("====== 全局提示词初始化完成 ======");
    }

    public void loadPrompt(String keyStr) {
        String dbPrompt = null;
        try {
            dbPrompt = promptService.getPrompt(keyStr, "zh-CN");
        } catch (Exception e) {
            log.warn("从数据库加载 Prompt [{}] 失败: {}", keyStr, e.getMessage());
        }

        if (dbPrompt != null && StrUtil.isNotBlank(dbPrompt) && dbPrompt.length() > 5) {
            promptCache.put(keyStr, dbPrompt);
            log.info("成功挂载提示词 [{}] - 来源: [Database], 长度: {} 字符", keyStr, dbPrompt.length());
            return;
        }

        // Try classpath fallback based on key
        String classpathFileName = keyStr + "-prompt.txt";

        try {
            ClassPathResource resource = new ClassPathResource(classpathFileName);
            if (resource.exists()) {
                String cpPrompt = IoUtil.read(resource.getInputStream(), StandardCharsets.UTF_8);
                if (StrUtil.isNotBlank(cpPrompt)) {
                    promptCache.put(keyStr, cpPrompt);
                    log.info("成功挂载提示词 [{}] - 来源: [Classpath/{}], 长度: {} 字符", keyStr, classpathFileName,
                            cpPrompt.length());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("从 Classpath 读取提示词文件 [{}] 发生异常", classpathFileName, e);
        }

        // Hardcoded fallbacks if file doesn't exist
        String hardcoded = getHardcodedFallback(keyStr);
        promptCache.put(keyStr, hardcoded);
        log.info("成功挂载提示词 [{}] - 来源: [Hardcoded Fallback], 长度: {} 字符", keyStr, hardcoded.length());
    }

    private String getHardcodedFallback(String keyStr) {
        if (PromptKey.CHAT.getKey().equals(keyStr)) {
            return "你是 Yusi，一位温暖、富有同理心的 AI 灵魂伴侣。";
        }
        if (PromptKey.MEMORY_EXTRACT.getKey().equals(keyStr)) {
            return "请你作为一位极其敏锐的观察者，阅读以下用户与 AI 的对话记录。\n" +
                    "你的任务是：提取出这段对话中用户最重要的信息、经历、情绪或观点。\n\n" +
                    "提取规则：\n" +
                    "1. 请以第三人称（或\"用户\"）的视角进行客观总结。\n" +
                    "2. 仅保留能够构成长久回忆的**关键事件**，忽略寒暄、无关紧要的闲聊等。\n" +
                    "3. 提取结果必须精简、具体。\n\n" +
                    "输出格式（只输出总结的结果，不要其他的任何废话）：\n";
        }
        if (PromptKey.GRAPHRAG_EXTRACT.getKey().equals(keyStr)) {
            return """
                    你正在为用户构建“人生图谱”（GraphRAG）。请从日记中抽取实体、关系与证据片段，并输出严格 JSON。

                    输出要求：
                    1) 只输出一个 JSON 对象，不要输出任何额外文字
                    2) JSON 结构：
                    {
                      "entities": [
                        {
                          "type": "Person|Event|Place|Emotion|Topic|Item",
                          "displayName": "原文中的称呼或新实体名称",
                          "nameNorm": "归一化名称（尽量映射到已知实体库的规范名；若为新实体则给出合理规范名）",
                          "aliases": ["别名1","别名2"],
                          "summary": "实体的一句话摘要，描述该实体在用户生活中的意义",
                          "emotion": "该实体关联的主要情绪（如：Joy/Sadness/Anxiety/Love/Anger/Fear/Hope/Calm/Confusion/Neutral）",
                          "importance": 0.5,
                          "confidence": 0.0,
                          "props": {}
                        }
                      ],
                      "relations": [
                        {
                          "source": "__USER__|nameNorm",
                          "target": "nameNorm",
                          "type": "RELATED_TO|HAPPENED_AT|TRIGGERED|PARTICIPATED|MENTIONED_IN",
                          "confidence": 0.0,
                          "props": {},
                          "evidenceSnippet": "可选，<=200字"
                        }
                      ],
                      "mentions": [
                        {
                          "entity": "nameNorm",
                          "snippet": "证据片段，<=200字",
                          "props": {}
                        }
                      ]
                    }

                    字段说明：
                    - summary: 必填，用一句话概括该实体对用户的意义
                    - emotion: 可选，该实体在上下文中引发的主要情绪
                    - importance: 0.1-1.0，评估该实体对用户的重要程度

                    抽取原则：
                    1) 若无法确定映射：优先使用已知实体库；仍不确定则创建新实体，但把可能别名放到 aliases
                    2) 关系置信度：LLM 自动抽取建议在 0.6-0.9 区间
                    3) summary 要具体，避免泛泛而谈
                    4) importance 要综合考虑：提及频率、情感强度、对用户生活的影响程度
                    """;
        }
        if (PromptKey.GRAPHRAG_MERGE_SUGGEST.getKey().equals(keyStr)) {
            return """
                    你将获得若干“疑似重复实体”的候选对。请评估每一对候选人是否指向同一个实际事物。如果是指代同一事物，请给出是否建议合并（YES/NO）、原因、推荐保留的规范名。

                    请务必只输出严格的 JSON 数组，格式如下：
                    [
                      {
                        "merge": "YES或NO",
                        "reason": "原因说明",
                        "recommendedMasterName": "推荐名"
                      }
                    ]
                    """;
        }
        if (PromptKey.COGNITION_ROUTING.getKey().equals(keyStr)) {
            return """
                    你是统一 AI Agent 的认知分流器。请根据输入文本，抽取两类信息：
                    1. 适合进入 user_persona 的稳定偏好信息
                    2. 适合进入 mid_memory 的近期状态信息

                    输出要求：
                    1. 只输出严格 JSON，不要输出任何额外文字
                    2. 如果某类信息不足，请返回空字符串或 null，不要编造

                    JSON 结构如下：
                    {
                      "preferredName": "",
                      "location": "",
                      "interests": "",
                      "tone": "",
                      "customInstructions": "",
                      "midMemorySummary": "",
                      "midMemoryImportance": 0.6
                    }

                    抽取原则：
                    - preferredName: 仅当用户明确表达希望被怎么称呼
                    - location: 仅当输入呈现较稳定的居住地/城市信息
                    - interests: 仅提取相对稳定的兴趣偏好
                    - tone/customInstructions: 仅提取长期有效的相处偏好
                    - midMemorySummary: 总结用户当前阶段最值得记住的近期状态
                    - midMemoryImportance: 取值 0.1-1.0
                    - 不要泄露隐私细节，不要原样复述手机号、单位名、真实姓名
                    """;
        }
        return "请作为一名 AI 助手回答问题。";
    }

    public String getPrompt(String keyStr) {
        return promptCache.getOrDefault(keyStr, "");
    }

    public String getPrompt(PromptKey key) {
        return getPrompt(key.getKey());
    }

    @EventListener
    public void handlePromptUpdated(PromptUpdatedEvent event) {
        if (StrUtil.isNotBlank(event.getPromptName())) {
            log.info("接收到提示词更新事件 [{}]，触发系统热重载...", event.getPromptName());
            loadPrompt(event.getPromptName());
        }
    }
}
