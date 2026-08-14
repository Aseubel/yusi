package com.aseubel.yusi.service.task;

import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskExecutionCommand {

    TaskExecutionType taskType;
    String ownerUserId;
    String sourceType;
    String sourceId;
    String sourceVersion;
    String triggerEventId;
    String runId;
    String idempotencyKey;
    Integer maxRetries;
    String checkpointJson;
}
