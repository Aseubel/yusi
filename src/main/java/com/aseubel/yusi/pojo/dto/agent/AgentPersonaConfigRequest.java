package com.aseubel.yusi.pojo.dto.agent;

import lombok.Data;

/**
 * Agent 人格配置请求 DTO。
 *
 * @author Aseubel
 * @date 2026/06/02
 */
@Data
public class AgentPersonaConfigRequest {

    /** 人格风格：gentle / lively / calm / rational */
    private String personalityStyle;

    /** 主动问候频率：off / low / normal */
    private String proactiveFrequency;

    /** 静默时段开始（HH:mm），null 表示不限 */
    private String quietHoursStart;

    /** 静默时段结束（HH:mm） */
    private String quietHoursEnd;

    /** 纪念日提醒开关 */
    private Boolean anniversaryReminderEnabled;

    /** 周报开关 */
    private Boolean weeklyReportEnabled;
}
