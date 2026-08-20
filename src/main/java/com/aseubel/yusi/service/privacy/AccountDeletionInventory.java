package com.aseubel.yusi.service.privacy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In-memory deletion inventory. Its string form deliberately contains counts
 * only so accidental diagnostic output cannot disclose identifiers or object keys.
 */
public final class AccountDeletionInventory {

    private final String targetUserId;
    private final Set<String> imageObjectKeys = new LinkedHashSet<>();
    private final Set<String> audioObjectKeys = new LinkedHashSet<>();
    private final Set<String> attachmentObjectKeys = new LinkedHashSet<>();
    private final Set<String> chunkObjectKeys = new LinkedHashSet<>();
    private final Set<String> exactRedisKeys = new LinkedHashSet<>();
    private final Set<UsageField> usageFields = new LinkedHashSet<>();
    private final Set<String> diaryIds = new LinkedHashSet<>();
    private final Set<String> graphEntityIds = new LinkedHashSet<>();
    private final Set<String> graphRelationIds = new LinkedHashSet<>();
    private final Set<String> soulMatchIds = new LinkedHashSet<>();
    private final Set<String> soulConnectionIds = new LinkedHashSet<>();
    private final Set<String> productEventIds = new LinkedHashSet<>();
    private final Set<String> runIds = new LinkedHashSet<>();

    public AccountDeletionInventory(String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("target user is required");
        }
        this.targetUserId = targetUserId;
    }

    public String targetUserId() {
        return targetUserId;
    }

    public Set<String> imageObjectKeys() {
        return Collections.unmodifiableSet(imageObjectKeys);
    }

    public Set<String> audioObjectKeys() {
        return Collections.unmodifiableSet(audioObjectKeys);
    }

    public Set<String> attachmentObjectKeys() {
        return Collections.unmodifiableSet(attachmentObjectKeys);
    }

    public Set<String> chunkObjectKeys() {
        return Collections.unmodifiableSet(chunkObjectKeys);
    }

    public Set<String> exactRedisKeys() {
        return Collections.unmodifiableSet(exactRedisKeys);
    }

    public Set<UsageField> usageFields() {
        return Collections.unmodifiableSet(usageFields);
    }

    public Set<String> diaryIds() {
        return Collections.unmodifiableSet(diaryIds);
    }

    public Set<String> graphEntityIds() {
        return Collections.unmodifiableSet(graphEntityIds);
    }

    public Set<String> graphRelationIds() {
        return Collections.unmodifiableSet(graphRelationIds);
    }

    public Set<String> soulMatchIds() {
        return Collections.unmodifiableSet(soulMatchIds);
    }

    public Set<String> soulConnectionIds() {
        return Collections.unmodifiableSet(soulConnectionIds);
    }

    public Set<String> productEventIds() {
        return Collections.unmodifiableSet(productEventIds);
    }

    public Set<String> runIds() {
        return Collections.unmodifiableSet(runIds);
    }

    void addImageObjectKey(String key) {
        addNonBlank(imageObjectKeys, key);
    }

    void addAudioObjectKey(String key) {
        addNonBlank(audioObjectKeys, key);
    }

    void addAttachmentObjectKey(String key) {
        addNonBlank(attachmentObjectKeys, key);
    }

    void addChunkObjectKey(String key) {
        addNonBlank(chunkObjectKeys, key);
    }

    void addExactRedisKey(String key) {
        addNonBlank(exactRedisKeys, key);
    }

    void addUsageField(String redisKey, String field) {
        if (redisKey != null && !redisKey.isBlank() && field != null && !field.isBlank()) {
            usageFields.add(new UsageField(redisKey, field));
        }
    }

    void addDiaryId(String diaryId) {
        addNonBlank(diaryIds, diaryId);
    }

    void addGraphEntityId(Object id) {
        addNonBlank(graphEntityIds, id == null ? null : String.valueOf(id));
    }

    void addGraphRelationId(Object id) {
        addNonBlank(graphRelationIds, id == null ? null : String.valueOf(id));
    }

    void addSoulMatchId(Object id) {
        addNonBlank(soulMatchIds, id == null ? null : String.valueOf(id));
    }

    void addSoulConnectionId(Object id) {
        addNonBlank(soulConnectionIds, id == null ? null : String.valueOf(id));
    }

    void addProductEventId(Object id) {
        addNonBlank(productEventIds, id == null ? null : String.valueOf(id));
    }

    void addRunId(Object id) {
        addNonBlank(runIds, id == null ? null : String.valueOf(id));
    }

    private void addNonBlank(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }

    @Override
    public String toString() {
        return "AccountDeletionInventory{"
                + "imageCount=" + imageObjectKeys.size()
                + ", audioCount=" + audioObjectKeys.size()
                + ", attachmentCount=" + attachmentObjectKeys.size()
                + ", chunkCount=" + chunkObjectKeys.size()
                + ", redisKeyCount=" + exactRedisKeys.size()
                + ", usageFieldCount=" + usageFields.size()
                + ", diaryCount=" + diaryIds.size()
                + ", graphEntityCount=" + graphEntityIds.size()
                + ", graphRelationCount=" + graphRelationIds.size()
                + ", matchCount=" + soulMatchIds.size()
                + ", connectionCount=" + soulConnectionIds.size()
                + ", eventCount=" + productEventIds.size()
                + ", runCount=" + runIds.size()
                + '}';
    }

    public record UsageField(String redisKey, String field) {
    }
}
