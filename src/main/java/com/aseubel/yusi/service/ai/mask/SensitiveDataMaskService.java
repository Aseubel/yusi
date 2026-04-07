package com.aseubel.yusi.service.ai.mask;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏服务
 * <p>
 * 在调用外部大模型 API 前，使用 HanLP NER + 正则规则对明文进行脱敏处理，
 * API 返回后通过映射表还原，实现"敏感信息不出域"。
 * <p>
 * 映射表仅在单次 mask→unmask 调用链中通过内存传递，无需持久化。
 *
 * @author Yusi
 */
@Slf4j
@Service
public class SensitiveDataMaskService {

    // ── 正则模式 ──────────────────────────────

    /** 中国大陆手机号：1[3-9]开头的11位数字 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 电子邮箱 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    /** 身份证号（18位） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");

    /**
     * 对明文进行脱敏
     *
     * @param plainText 明文
     * @return 脱敏结果（含映射表）
     */
    public MaskResult mask(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return MaskResult.empty();
        }

        try {
            return doMask(plainText);
        } catch (Exception e) {
            log.error("脱敏处理失败，降级为不脱敏", e);
            return MaskResult.noMask(plainText);
        }
    }

    /**
     * 对脱敏文本进行还原
     *
     * @param mappingTable 占位符→原始值映射表
     * @param maskedText   脱敏文本
     * @return 还原后的明文
     */
    public String unmask(Map<String, String> mappingTable, String maskedText) {
        if (mappingTable == null || mappingTable.isEmpty() || maskedText == null) {
            return maskedText;
        }

        String result = maskedText;
        for (Map.Entry<String, String> entry : mappingTable.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ── 内部逻辑 ──────────────────────────────

    private MaskResult doMask(String text) {
        // 收集所有识别到的实体：offset → (原始文本, 类型)
        // 使用 TreeMap 按偏移量排序，方便从后往前替换
        TreeMap<Integer, DetectedEntity> detected = new TreeMap<>(Comparator.reverseOrder());

        // 1) HanLP 词性标注识别人名/地名/机构名
        detectByHanLP(text, detected);

        // 2) 正则识别电话/邮箱/身份证
        detectByRegex(text, PHONE_PATTERN, SensitiveEntityType.PHONE, detected);
        detectByRegex(text, EMAIL_PATTERN, SensitiveEntityType.EMAIL, detected);
        detectByRegex(text, ID_CARD_PATTERN, SensitiveEntityType.ID_CARD, detected);

        if (detected.isEmpty()) {
            return MaskResult.noMask(text);
        }

        // 3) 生成映射表并替换（从后往前替换以保持偏移量正确）
        Map<String, String> mappingTable = new LinkedHashMap<>();
        Map<SensitiveEntityType, Integer> typeCounters = new EnumMap<>(SensitiveEntityType.class);
        // 去重：同一原始值只分配一个占位符
        Map<String, String> valueToPlaceholder = new HashMap<>();

        StringBuilder sb = new StringBuilder(text);

        for (Map.Entry<Integer, DetectedEntity> entry : detected.entrySet()) {
            int offset = entry.getKey();
            DetectedEntity entity = entry.getValue();

            String placeholder = valueToPlaceholder.get(entity.text);
            if (placeholder == null) {
                int count = typeCounters.merge(entity.type, 1, Integer::sum);
                placeholder = entity.type.getPlaceholder(count);
                valueToPlaceholder.put(entity.text, placeholder);
                mappingTable.put(placeholder, entity.text);
            }

            sb.replace(offset, offset + entity.text.length(), placeholder);
        }

        log.info("脱敏完成: 识别 {} 个实体, 类型分布={}", detected.size(), typeCounters);

        return new MaskResult(sb.toString(), mappingTable, true);
    }

    /**
     * 使用 HanLP 分词+词性标注检测人名/地名/机构名
     */
    private void detectByHanLP(String text, TreeMap<Integer, DetectedEntity> detected) {
        List<Term> terms = HanLP.segment(text);
        int offset = 0;

        for (Term term : terms) {
            // 修正偏移量：HanLP term.offset 在 portable 版中可能不准确，手动计算
            int idx = text.indexOf(term.word, offset);
            if (idx < 0) {
                offset += term.word.length();
                continue;
            }

            SensitiveEntityType type = SensitiveEntityType.fromNatureTag(term.nature.toString());
            if (type != null && term.word.length() >= 2) {
                // 只保留长度 >= 2 的实体（避免单字误识别）
                detected.put(idx, new DetectedEntity(term.word, type));
            }

            offset = idx + term.word.length();
        }
    }

    /**
     * 使用正则表达式检测特定模式的实体
     */
    private void detectByRegex(String text, Pattern pattern, SensitiveEntityType type,
                               TreeMap<Integer, DetectedEntity> detected) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            // 检查是否与已检测到的实体重叠
            if (!isOverlapping(start, matcher.end(), detected)) {
                detected.put(start, new DetectedEntity(matcher.group(), type));
            }
        }
    }

    /**
     * 检查新区间 [start, end) 是否与已有实体重叠
     */
    private boolean isOverlapping(int start, int end, TreeMap<Integer, DetectedEntity> detected) {
        for (Map.Entry<Integer, DetectedEntity> entry : detected.entrySet()) {
            int existStart = entry.getKey();
            int existEnd = existStart + entry.getValue().text.length();
            if (start < existEnd && end > existStart) {
                return true;
            }
        }
        return false;
    }

    /** 内部检测到的实体 */
    private record DetectedEntity(String text, SensitiveEntityType type) {}
}
