package com.aseubel.yusi.config.ai.properties;

import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModelTierDefinition {

    private String displayName;

    private String description;

    private List<String> members = new ArrayList<>();

    private ModelSelectionStrategyType strategy = ModelSelectionStrategyType.ROUND_ROBIN;

    private boolean enabled = true;

    private List<ModelCapability> capabilities = new ArrayList<>();

    /**
     * Tier 级思考模式开关（场景覆盖）：route 解析到 primary-tier 后，
     * 非空值将覆盖成员模型的 {@code ModelDefinition.thinkingEnabled}，
     * 使同一模型可在不同场景下差异化开关思考链（如 GraphRAG 抽取关闭、聊天开启）。
     * null 表示不覆盖，沿用模型级配置或服务端默认。
     */
    private Boolean thinkingEnabled;
}
