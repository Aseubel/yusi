package com.aseubel.yusi.service.agent;

/**
 * Agent 主动行为服务。
 * 负责判断是否应主动向用户发起关怀、问候或回顾邀请。
 *
 * @author Aseubel
 * @date 2026/06/02
 */
public interface AgentProactiveService {

    /**
     * 扫描所有开启了主动问候的用户，判断是否满足触发条件。
     * 由定时任务调用（建议每小时一次）。
     */
    void scanAndGreet();
}
