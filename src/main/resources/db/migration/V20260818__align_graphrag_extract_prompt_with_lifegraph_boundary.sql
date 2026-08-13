INSERT INTO `prompt_template`
    (`name`, `template`, `version`, `active`, `scope`, `locale`, `description`, `tags`, `is_default`, `priority`, `updated_by`)
VALUES
    (
        'graphrag-extract',
        '你正在为用户构建人生图谱（GraphRAG）。请从日记或用户本人发布的 Plaza 卡片中，宽范围抽取局部候选实体、生活关系和原文证据，并只输出严格 JSON。\n\n边界：\n1. 抽取范围宽：识别 Person、Event、Place、Work、Topic、Emotion、Item、User。抽取结果首先是当前文本的局部上下文，不等于长期 LifeGraph 事实。\n2. 长期升级严格：只有与用户本人或已确认的重要人物存在明确生活语义关系、且有原文证据的实体和关系，才适合进入长期图谱。背景知识、引用、转述和与用户生活无关的实体不要升级。\n3. 自动升级边界是 User -> 直接重要人物 -> 该人物的属性或事件。不要仅通过图关系继续升级人物的同事、朋友或其他人物；Person -> Person 的自动扩展默认拒绝。这个边界不限制 GraphRAG 查询多跳遍历。\n4. 不要使用 MENTIONED、MENTIONED_IN、SAID 或泛化 RELATED_TO 填充长期关系。关系必须有明确方向、长期生活语义和来自原文的证据片段。\n\nJSON 结构：\n{\n  "entities": [\n    {\n      "type": "Person|Event|Place|Work|Topic|Emotion|Item|User",\n      "displayName": "原文中的称呼或实体名称",\n      "nameNorm": "归一化名称；新人物使用原文称呼或全名，不使用女朋友、同事或英文身份标签；用户使用 __USER__",\n      "aliases": [],\n      "summary": "基于原文的一句话摘要，不编造",\n      "emotion": "Joy|Sadness|Anxiety|Love|Anger|Fear|Hope|Calm|Confusion|Neutral",\n      "importance": 0.5,\n      "confidence": 0.0,\n      "props": {}\n    }\n  ],\n  "relations": [\n    {\n      "source": "__USER__|nameNorm",\n      "target": "nameNorm",\n      "type": "PARTNER_OF|FAMILY_OF|FRIEND_OF|COLLEAGUE_OF|MENTOR_OF|SIBLING_OF|PARENT_OF|CHILD_OF|LIKES|DISLIKES|BOUGHT_FOR|PARTICIPATED_IN|EXPERIENCED|HAPPENED_AT|TRIGGERED|WORKED_AT|LIVED_AT|CARED_FOR|HAS_BIRTHDAY|HAS_IMPORTANT_EVENT|VISITED|ATTENDED",\n      "confidence": 0.0,\n      "props": {},\n      "evidenceSnippet": "必填，来自原文，<=100字"\n    }\n  ],\n  "mentions": [\n    {\n      "entity": "nameNorm",\n      "snippet": "来自原文的短证据，<=100字",\n      "props": {}\n    }\n  ]\n}\n\n判断规则：\n- 用户明确表达我的女朋友小美、妈妈、重要朋友等直接关系时，才输出 User -> Person 关系。\n- 已确认的重要人物可以与非 Person 的长期属性或事件建立关系，例如小美 -> LIKES -> 草莓。\n- 用户对重要人物的明确赠与或照顾可以输出 User -> BOUGHT_FOR 或 User -> CARED_FOR。\n- 小美的同事小王喜欢篮球不应创建小王及其喜好。\n- 不确定时省略实体关系，不要用泛化关系补全图谱。importance 表示长期回顾和个性化互动价值，不是出现次数。服务端会再次校验实体类型、关系类型、证据、来源和置信度。',
        'v2',
        1,
        'graph',
        'zh-CN',
        '人生图谱：宽抽取、严格升级、用户重要人物和来源证据',
        'graphrag,extract,life-graph,provenance',
        1,
        100,
        'SYSTEM'
    )
ON DUPLICATE KEY UPDATE
    `template` = VALUES(`template`),
    `version` = VALUES(`version`),
    `active` = VALUES(`active`),
    `scope` = VALUES(`scope`),
    `description` = VALUES(`description`),
    `tags` = VALUES(`tags`),
    `is_default` = VALUES(`is_default`),
    `priority` = VALUES(`priority`),
    `updated_by` = VALUES(`updated_by`),
    `updated_at` = CURRENT_TIMESTAMP;
