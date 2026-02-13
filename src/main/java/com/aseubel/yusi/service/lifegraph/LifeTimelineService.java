package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.service.lifegraph.dto.LifeChapter;
import com.aseubel.yusi.service.lifegraph.dto.TimelineNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人生时间线服务
 * 
 * 负责聚合 Event 类型的实体，生成按时间排序的节点和章节。
 * 
 * @author Aseubel
 * @date 2026/02/10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LifeTimelineService {

    private final LifeGraphEntityRepository entityRepository;

    /**
     * 生成用户的人生章节（聚类后的时间线）
     */
    public List<LifeChapter> getLifeChapters(String userId) {
        // 1. 获取所有 Event 类型的实体
        List<LifeGraphEntity> events = entityRepository.findByUserIdAndType(userId, LifeGraphEntity.EntityType.Event);
        
        // 过滤掉没有日期的事件
        List<TimelineNode> nodes = events.stream()
                .filter(e -> e.getFirstMentionDate() != null)
                .map(this::toNode)
                .sorted(Comparator.comparing(TimelineNode::getDate))
                .collect(Collectors.toList());

        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 简单聚类：按"月份"断裂或"时间间隔 > 30天"切分章节
        return clusterIntoChapters(nodes);
    }

    private List<LifeChapter> clusterIntoChapters(List<TimelineNode> nodes) {
        List<LifeChapter> chapters = new ArrayList<>();
        if (nodes.isEmpty()) return chapters;

        List<TimelineNode> currentBuffer = new ArrayList<>();
        TimelineNode firstNode = nodes.get(0);
        currentBuffer.add(firstNode);

        for (int i = 1; i < nodes.size(); i++) {
            TimelineNode current = nodes.get(i);
            TimelineNode prev = nodes.get(i - 1);

            // 规则：如果两个事件间隔超过 60 天，或者跨越了年份，则开启新章节
            long daysDiff = ChronoUnit.DAYS.between(prev.getDate(), current.getDate());
            boolean yearChanged = prev.getDate().getYear() != current.getDate().getYear();

            if (daysDiff > 60 || yearChanged) {
                // 结算当前章节
                chapters.add(createChapter(currentBuffer));
                currentBuffer = new ArrayList<>();
            }
            currentBuffer.add(current);
        }

        // 结算最后一个章节
        if (!currentBuffer.isEmpty()) {
            chapters.add(createChapter(currentBuffer));
        }

        // 倒序排列章节（最近的在前面）
        Collections.reverse(chapters);
        return chapters;
    }

    private LifeChapter createChapter(List<TimelineNode> nodes) {
        if (nodes.isEmpty()) return null;
        
        LocalDate start = nodes.get(0).getDate();
        LocalDate end = nodes.get(nodes.size() - 1).getDate();
        
        // 自动生成标题
        String title = generateChapterTitle(start, end, nodes);
        
        // 提取高频关键词 (简单取 Top 3 事件名)
        List<String> keywords = nodes.stream()
                .sorted(Comparator.comparingDouble(TimelineNode::getImportance).reversed())
                .limit(3)
                .map(TimelineNode::getTitle)
                .collect(Collectors.toList());

        return LifeChapter.builder()
                .title(title)
                .startDate(start)
                .endDate(end)
                .nodes(nodes) // 节点保持正序
                .keywords(keywords)
                .summary(nodes.size() + " 个重要时刻")
                .build();
    }

    private String generateChapterTitle(LocalDate start, LocalDate end, List<TimelineNode> nodes) {
        // 简单逻辑：如果跨度小，用月份；如果跨度大，用年份/季节
        // 优先使用最高权重的事件名作为章节副标题
        
        String timeLabel;
        if (start.getYear() == end.getYear()) {
            if (start.getMonth() == end.getMonth()) {
                timeLabel = DateUtil.format(start.atStartOfDay(), "yyyy年M月");
            } else {
                timeLabel = start.getYear() + "年" + start.getMonthValue() + "-" + end.getMonthValue() + "月";
            }
        } else {
            timeLabel = start.getYear() + " - " + end.getYear();
        }

        // 找到最重要的事件
        Optional<TimelineNode> topEvent = nodes.stream()
                .max(Comparator.comparingDouble(TimelineNode::getImportance));
        
        if (topEvent.isPresent() && topEvent.get().getImportance() > 0.8) {
            return timeLabel + " · " + topEvent.get().getTitle();
        }
        
        return timeLabel;
    }

    private TimelineNode toNode(LifeGraphEntity e) {
        // 计算权重: 基础提及次数 + 关系数加成
        double importance = Math.min(1.0, (e.getMentionCount() * 0.1) + (e.getRelationCount() * 0.05));
        
        return TimelineNode.builder()
                .entityId(e.getId())
                .title(e.getDisplayName())
                .date(e.getFirstMentionDate())
                .summary(e.getSummary())
                .importance(importance)
                // .emotion() // 暂时没有直接的情绪字段，后续可扩展
                .build();
    }
}
