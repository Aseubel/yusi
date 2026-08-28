package com.aseubel.yusi.service.privacy;

import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.oss.OssService;
import com.aseubel.yusi.service.user.TokenService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Production adapter with exact-key and allow-listed object operations. */
@Service
public class DefaultAccountDeletionExternalPort implements AccountDeletionExternalPort {

    private static final int MAX_OBJECT_DELETE_BATCH_SIZE = 100;
    private static final int MAX_CHUNK_SESSION_PARTS = 1000;
    private static final String CHUNK_PREFIX = "yusi:chunk:";
    private static final String TOTAL_CHUNKS_SUFFIX = ":totalChunks";
    private static final String VIOLATION_KEY_PREFIX = "yusi:violation:count:";

    private final MilvusClientV2 milvusClientV2;
    private final IRedisService redisService;
    private final TokenService tokenService;
    private final OssService ossService;
    private final com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties collectionProperties;

    @Autowired
    public DefaultAccountDeletionExternalPort(MilvusClientV2 milvusClientV2,
            IRedisService redisService, TokenService tokenService,
            ObjectProvider<OssService> ossServiceProvider,
            com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties collectionProperties) {
        this(milvusClientV2, redisService, tokenService,
                ossServiceProvider == null ? null : ossServiceProvider.getIfAvailable(),
                collectionProperties);
    }

    public DefaultAccountDeletionExternalPort(MilvusClientV2 milvusClientV2,
            IRedisService redisService, TokenService tokenService, OssService ossService,
            com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties collectionProperties) {
        this.milvusClientV2 = milvusClientV2;
        this.redisService = redisService;
        this.tokenService = tokenService;
        this.ossService = ossService;
        this.collectionProperties = collectionProperties;
    }

    @Override
    public void deleteMilvus(AccountDeletionInventory inventory) {
        String targetUserId = escapeFilterValue(inventory.targetUserId());
        deleteCollection(collectionProperties.getEmbedding(), "metadata[\"userId\"] == \"" + targetUserId + "\"");
        deleteCollection(collectionProperties.getMidTermMemory(), "metadata[\"userId\"] == \"" + targetUserId + "\"");
        deleteCollection(collectionProperties.getMatchProfile(),
                "id == \"" + targetUserId + "\" || metadata[\"userId\"] == \"" + targetUserId + "\"");
    }

    @Override
    public void deleteRedis(AccountDeletionInventory inventory) {
        String targetUserId = inventory.targetUserId();
        tokenService.deleteRefreshToken(targetUserId);
        tokenService.removeAllDeviceTokens(targetUserId);
        redisService.remove("yusi:langchain:" + targetUserId);
        redisService.remove(VIOLATION_KEY_PREFIX + targetUserId);
        redisService.removeUsageFields(targetUserId);

        for (AccountDeletionInventory.UsageField usageField : inventory.usageFields()) {
            redisService.removeFromMap(usageField.redisKey(), usageField.field());
        }
        for (String redisKeyPattern : inventory.redisKeyPatterns()) {
            redisService.removeByPattern(redisKeyPattern);
        }
        for (String exactRedisKey : inventory.exactRedisKeys()) {
            redisService.remove(exactRedisKey);
        }
    }

    @Override
    public void deleteObjects(AccountDeletionInventory inventory) {
        if (ossService == null) {
            return;
        }
        List<String> imageKeys = new ArrayList<>();
        imageKeys.addAll(inventory.imageObjectKeys());
        imageKeys.addAll(inventory.attachmentObjectKeys());
        for (int start = 0; start < imageKeys.size(); start += MAX_OBJECT_DELETE_BATCH_SIZE) {
            int end = Math.min(start + MAX_OBJECT_DELETE_BATCH_SIZE, imageKeys.size());
            ossService.deleteOwnedImages(imageKeys.subList(start, end), inventory.targetUserId());
        }
        ossService.deleteOwnedImagePrefix(inventory.targetUserId());
        ossService.deleteOwnedAudioPrefix(inventory.targetUserId());
        for (String audioKey : inventory.audioObjectKeys()) {
            ossService.deleteOwnedAudioObject(audioKey, inventory.targetUserId());
        }
        for (String chunkKey : inventory.chunkObjectKeys()) {
            ossService.deleteOwnedChunkObject(chunkKey, inventory.targetUserId());
        }
        deleteSessionChunkObjects(inventory);
    }

    private void deleteSessionChunkObjects(AccountDeletionInventory inventory) {
        Set<String> chunkObjectKeys = new LinkedHashSet<>();
        for (String exactRedisKey : inventory.exactRedisKeys()) {
            if (!exactRedisKey.startsWith(CHUNK_PREFIX)
                    || !exactRedisKey.endsWith(TOTAL_CHUNKS_SUFFIX)) {
                continue;
            }
            String totalChunksValue = redisService.getStringValue(exactRedisKey);
            if (totalChunksValue == null || totalChunksValue.isBlank()) {
                continue;
            }
            int totalChunks;
            try {
                totalChunks = Integer.parseInt(totalChunksValue);
            } catch (NumberFormatException exception) {
                throw new AccountDeletionFailure("chunk_inventory_invalid");
            }
            if (totalChunks < 1 || totalChunks > MAX_CHUNK_SESSION_PARTS) {
                throw new AccountDeletionFailure("chunk_inventory_invalid");
            }
            String chunkPrefix = exactRedisKey.substring(0,
                    exactRedisKey.length() - TOTAL_CHUNKS_SUFFIX.length());
            for (int index = 0; index < totalChunks; index++) {
                String chunkObjectKey = redisService.getStringValue(chunkPrefix + ":" + index);
                if (chunkObjectKey != null && !chunkObjectKey.isBlank()) {
                    chunkObjectKeys.add(chunkObjectKey);
                }
            }
        }
        for (String chunkObjectKey : chunkObjectKeys) {
            ossService.deleteOwnedChunkObject(chunkObjectKey, inventory.targetUserId());
        }
        ossService.deleteOwnedChunkPrefix(inventory.targetUserId());
    }

    private void deleteCollection(String collectionName, String filter) {
        ensureCollectionLoaded(collectionName);
        milvusClientV2.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .build());
    }

    /**
     * Milvus 空闲会自动释放 collection；删除前确保已加载，否则 delete 抛
     * "collection not loaded" 导致整个注销流程被判 external_or_database。
     */
    private void ensureCollectionLoaded(String collectionName) {
        GetLoadStateReq stateReq = GetLoadStateReq.builder().collectionName(collectionName).build();
        try {
            if (Boolean.TRUE.equals(milvusClientV2.getLoadState(stateReq))) {
                return;
            }
            milvusClientV2.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
            long deadline = System.currentTimeMillis() + 60_000L;
            while (System.currentTimeMillis() < deadline) {
                if (Boolean.TRUE.equals(milvusClientV2.getLoadState(stateReq))) {
                    return;
                }
                Thread.sleep(1_000L);
            }
            throw new AccountDeletionFailure("milvus_collection_load_timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AccountDeletionFailure("milvus_collection_load_interrupted");
        }
    }

    private String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
