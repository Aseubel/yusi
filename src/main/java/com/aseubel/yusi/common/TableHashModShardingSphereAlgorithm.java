package com.aseubel.yusi.common;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
 
import java.util.Collection;
import java.util.List;
 
/**
* <p>
* description: 基于 HashMod 方式自定义分表算法,该算法通过对分片键值进行哈希计算并取模的方式，决定目标表。
* </p>
*
* @author: bluefoxyu
* @date: 2025-01-12 19:34:11
*/
public final class TableHashModShardingSphereAlgorithm implements StandardShardingAlgorithm<String> {
 
    /**
    * <p>
    * description: 精确分片算法
    * </p>
    *
    * @param availableTargetNames 可用的目标表名集合
    * @param shardingValue 分片值（包含逻辑表名和实际分片键值）
    * @return: 目标表名
    */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<String> shardingValue) {
        // 获取分片键值
        String id = shardingValue.getValue();
 
        // 获取目标表的数量
        int shardingCount = availableTargetNames.size();
 
        // 通过哈希值计算目标表索引
        int mod = (int) hashShardingValue(id) % shardingCount;
 
        // 根据索引返回对应的目标表名
        int index = 0;
        for (String targetName : availableTargetNames) {
            if (index == mod) {
                return targetName;
            }
            index++;
        }
 
        // 如果找不到目标表，则抛出异常
        throw new IllegalArgumentException("未找到适合的目标表，分片键值：" + id);
    }
 
    /**
    * <p>
    * description: 范围分片算法
    * </p>
    *
    * @param availableTargetNames 可用的目标表名集合
    * @param shardingValue 分片范围值
    */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<String> shardingValue) {
        // 目前未实现范围分片逻辑，默认返回空集合
        return List.of();
    }
 
    /**
    * <p>
    * description: 计算分片键的哈希值
    * </p>
    *
    * @param shardingValue 分片键值
    * @return: long 分片键的非负哈希值
    */
    private long hashShardingValue(final Comparable<?> shardingValue) {
        // 通过 hashCode 计算哈希值，并确保为非负数
        return Math.abs((long) shardingValue.hashCode());
    }
}