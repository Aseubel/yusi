package com.aseubel.yusi.service.diary.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.common.event.DiaryCognitionIngestEvent;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.common.utils.AesGcmCryptoUtils;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.constant.KeyMode;
import com.aseubel.yusi.pojo.constant.DiaryAttachmentAnchorKind;
import com.aseubel.yusi.pojo.constant.DiaryAttachmentType;
import com.aseubel.yusi.pojo.dto.diary.DiaryAttachmentAnchor;
import com.aseubel.yusi.pojo.dto.diary.DiaryAttachmentBinding;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.oss.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class DiaryServiceImpl implements DiaryService {

    private static final int MAX_ATTACHMENT_BINDINGS = 100;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private OssService ossService;

    @Autowired
    private SensitiveDataMaskService sensitiveDataMaskService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    @Lazy
    private DiaryService self;

    /**
     * 新增日记
     * 失效该用户的日记列表缓存
     */
    @Override
    @UpdateCache(key = "'diary:list:v4:' + #diary.userId + ':*'", evictOnly = true)
    public Diary addDiary(Diary diary) {
        validateDiaryAssets(diary);
        diary.generateId();
        diary.setCreateTime(LocalDateTime.now());
        diary.setUpdateTime(LocalDateTime.now());
        User user = userRepository.findByUserId(diary.getUserId());
        applyWriteCrypto(diary, user);
        String plainContent = diary.getPlainContent();
        Diary saved = diaryRepository.save(diary);
        // 保存后 entity 可能会丢失 transient 字段，这里重新设置以便后续 disruptor 使用
        saved.setPlainContent(plainContent);
        publishDiaryEvents(saved, plainContent, DiaryChangedEvent.Type.WRITE);

        self.evictFootprintsCache(diary.getUserId());
        return saved;
    }

    @Override
    public String decryptDiaryContent(Diary diary) {
        if (diary == null) {
            return null;
        }
        if (StrUtil.isNotBlank(diary.getPlainContent())) {
            return diary.getPlainContent();
        }
        if (StrUtil.isBlank(diary.getContent())) {
            return null;
        }

        User user = userRepository.findByUserId(diary.getUserId());
        if (user == null) {
            return null;
        }

        String keyMode = user.getKeyMode();
        if (keyMode == null || KeyMode.DEFAULT.code().equals(keyMode)) {
            return AesGcmCryptoUtils.decryptText(diary.getContent(), cryptoService.serverAesKeyBytes());
        }

        if (KeyMode.CUSTOM.code().equals(keyMode)) {
            if (!Boolean.TRUE.equals(user.getHasCloudBackup())) {
                return null;
            }
            if (StrUtil.isBlank(user.getEncryptedBackupKey())) {
                return null;
            }
            byte[] keyBytes = cryptoService.decryptBackupKeyBase64(user.getEncryptedBackupKey());
            if (keyBytes.length != 32) {
                return null;
            }
            return AesGcmCryptoUtils.decryptText(diary.getContent(), keyBytes);
        }

        return null;
    }

    /**
     * 编辑日记
     * 失效单个日记缓存和用户列表缓存
     */
    @Override
    @UpdateCache(key = "'diary:detail:v4:' + #diary.diaryId + ':' + #diary.userId", evictOnly = true)
    public Diary editDiary(Diary diary) {
        validateDiaryAssets(diary);
        Diary existingDiary = diaryRepository.findByDiaryIdAndUserId(diary.getDiaryId(), diary.getUserId());
        if (ObjectUtil.isNotEmpty(existingDiary)) {
            // Delete removed images from OSS
            deleteRemovedImages(existingDiary.getImages(), diary.getImages(), diary.getUserId());

            diary.setId(existingDiary.getId());
            diary.setUpdateTime(LocalDateTime.now());
            diary.setCreateTime(existingDiary.getCreateTime());
            User user = userRepository.findByUserId(diary.getUserId());
            applyWriteCrypto(diary, user);
            String plainContent = diary.getPlainContent();
            Diary saved = diaryRepository.save(diary);
            // 保存后 entity 可能会丢失 transient 字段，这里重新设置以便后续 disruptor 使用
            saved.setPlainContent(plainContent);
            publishDiaryEvents(saved, plainContent, DiaryChangedEvent.Type.MODIFY);
            self.evictListCache(diary.getUserId());
            self.evictFootprintsCache(diary.getUserId());
            return saved;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "日记不存在");
    }

    private void deleteRemovedImages(String oldImagesJson, String newImagesJson, String userId) {
        if (StrUtil.isBlank(oldImagesJson)) {
            return;
        }
        try {
            List<String> oldImages = JSONUtil.toList(oldImagesJson, String.class);
            List<String> newImages = StrUtil.isBlank(newImagesJson) ? new java.util.ArrayList<>()
                    : JSONUtil.toList(newImagesJson, String.class);

            List<String> deletedImages = oldImages.stream()
                    .filter(img -> !newImages.contains(img))
                    .collect(java.util.stream.Collectors.toList());

            if (!deletedImages.isEmpty()) {
                // Async deletion would be better, but we do it synchronously for simplicity
                ossService.deleteOwnedImages(deletedImages, userId);
                log.info("Deleted orphaned images from OSS: {}", deletedImages);
            }
        } catch (Exception e) {
            log.error("Failed to delete removed images from OSS during diary edit", e);
        }
    }

    @UpdateCache(key = "'diary:detail:v4:' + #diaryId + ':' + #userId", evictOnly = true)
    public void evictDiaryCache(String diaryId, String userId) {
    }

    /**
     * 失效用户日记列表缓存的辅助方法
     */
    @UpdateCache(key = "'diary:list:v4:' + #userId + ':*'", evictOnly = true)
    public void evictListCache(String userId) {
        // 空方法，仅用于触发缓存失效
    }

    @UpdateCache(key = "'diary:footprints:' + #userId", evictOnly = true)
    public void evictFootprintsCache(String userId) {
    }

    /**
     * 获取单个日记详情
     * 使用压缩缓存，日记内容较大，压缩可显著减少 Redis 内存占用
     */
    @Override
    public Diary getDiary(String diaryId, String userId) {
        Diary diary = self.getCachedDiary(diaryId, userId);
        if (diary != null) {
            enrichDiaryAssets(diary, userId);
        }
        return diary;
    }

    @Override
    @QueryCache(key = "'diary:detail:v4:' + #diaryId + ':' + #userId", ttl = 3600, compress = true)
    public Diary getCachedDiary(String diaryId, String userId) {
        Diary diary = diaryRepository.findByDiaryIdAndUserId(diaryId, userId);
        if (diary == null) {
            return null;
        }
        applyReadCrypto(diary);
        prepareDiaryAssets(diary);
        return diary;
    }

    /**
     * 获取日记列表
     * 使用压缩缓存，列表数据较大
     */
    @Override
    public Page<Diary> getDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc) {
        Page<Diary> page = self.getCachedDiaryList(userId, pageNum, pageSize, sortBy, asc);
        if (page.hasContent()) {
            page.getContent().forEach(diary -> enrichDiaryAssets(diary, userId));
        }
        return page;
    }

    @Override
    @QueryCache(key = "'diary:list:v4:' + #userId + ':' + #pageNum + ':' + #pageSize + ':' + #sortBy + ':' + #asc", ttl = 300, compress = true)
    public Page<Diary> getCachedDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc) {
        // 处理默认排序字段
        String actualSort = StrUtil.isBlank(sortBy) ? "entryDate" : sortBy;

        // 构建分页请求（注意Spring Data页码从0开始）
        Sort sort = Sort.by(asc ? Sort.Direction.DESC : Sort.Direction.ASC, actualSort);
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sort);

        Example<Diary> example = Example.of(Diary.builder().userId(userId).build());
        Page<Diary> page = diaryRepository.findAll(example, pageRequest);
        if (page.hasContent()) {
            page.getContent().forEach(diary -> {
                applyReadCrypto(diary);
                prepareDiaryAssets(diary);
            });
        }
        return page;

        // 如需带条件查询（示例）
        // return diaryRepository.findByUserId("当前用户ID", pageRequest);
    }

    /**
     * 获取用户足迹列表
     * 使用压缩缓存
     */
    @Override
    @QueryCache(key = "'diary:footprints:' + #userId", ttl = 600, compress = true)
    public List<Diary> getFootprints(String userId) {
        List<Diary> diaries = diaryRepository.findAllWithLocationByUserId(userId);
        diaries.forEach(this::applyReadCrypto);
        return diaries;
    }

    private void applyWriteCrypto(Diary diary, User user) {
        if (diary == null) {
            return;
        }
        if (user == null || user.getKeyMode() == null || KeyMode.DEFAULT.code().equals(user.getKeyMode())) {
            diary.setClientEncrypted(false);
            String plain = diary.getContent();
            diary.setPlainContent(plain);
            if (StrUtil.isNotBlank(plain)) {
                diary.setContent(AesGcmCryptoUtils.encryptText(plain, cryptoService.serverAesKeyBytes()));
            }
            return;
        }
            if (KeyMode.CUSTOM.code().equals(user.getKeyMode())) {
            diary.setClientEncrypted(true);
        }
    }

    private void applyReadCrypto(Diary diary) {
        if (diary == null) {
            return;
        }
        if (StrUtil.isBlank(diary.getContent())) {
            return;
        }
        if (Boolean.TRUE.equals(diary.getClientEncrypted())) {
            return;
        }
        try {
            diary.setContent(AesGcmCryptoUtils.decryptText(diary.getContent(), cryptoService.serverAesKeyBytes()));
        } catch (Exception e) {
            return;
        }
    }

    private void prepareDiaryAssets(Diary diary) {
        diary.setImageObjectKeys(parseImageObjectKeys(diary.getImages()));
        diary.setAttachmentBindings(parseAttachmentBindings(diary.getAttachmentBindingsJson()));
    }

    private void enrichDiaryAssets(Diary diary, String userId) {
        List<String> imageObjectKeys = diary.getImageObjectKeys();
        if (imageObjectKeys == null) {
            imageObjectKeys = parseImageObjectKeys(diary.getImages());
            diary.setImageObjectKeys(imageObjectKeys);
        }
        if (!imageObjectKeys.isEmpty()) {
            diary.setImages(JSONUtil.toJsonStr(ossService.generateOwnedUrls(imageObjectKeys, userId)));
        } else {
            diary.setImages(JSONUtil.toJsonStr(List.of()));
        }
        List<DiaryAttachmentBinding> bindings = diary.getAttachmentBindings();
        if (bindings == null) {
            bindings = parseAttachmentBindings(diary.getAttachmentBindingsJson());
        }
        diary.setAttachmentBindings(resolveAttachmentBindingsToUrls(bindings, userId));
    }

    @Override
    public String convertImagesToUrls(String imagesJson, String userId) {
        if (StrUtil.isBlank(imagesJson)) {
            return imagesJson;
        }
        List<String> objectKeys;
        try {
            objectKeys = JSONUtil.toList(imagesJson, String.class);
        } catch (RuntimeException e) {
            log.warn("日记图片字段不是有效 JSON: {}", imagesJson, e);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日记图片数据不合法");
        }
        return JSONUtil.toJsonStr(ossService.generateOwnedUrls(objectKeys, userId));
    }

    @Override
    public List<DiaryAttachmentBinding> convertAttachmentBindingsToUrls(String bindingsJson, String userId) {
        List<DiaryAttachmentBinding> bindings = parseAttachmentBindings(bindingsJson);
        if (bindings.isEmpty()) {
            return List.of();
        }
        return resolveAttachmentBindingsToUrls(bindings, userId);
    }

    private List<DiaryAttachmentBinding> resolveAttachmentBindingsToUrls(
            List<DiaryAttachmentBinding> bindings, String userId) {
        return bindings.stream()
                .map(binding -> DiaryAttachmentBinding.builder()
                        .type(binding.getType())
                        .objectKey(binding.getObjectKey())
                        .paragraphId(binding.getParagraphId())
                        .sortOrder(binding.getSortOrder())
                        .anchor(copyAttachmentAnchor(binding.getAnchor()))
                        .url(generateAttachmentUrl(binding, userId))
                        .build())
                .toList();
    }

    private void validateDiaryAssets(Diary diary) {
        if (diary == null || StrUtil.isBlank(diary.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户身份不能为空");
        }
        List<String> imageObjectKeys = parseImageObjectKeys(diary.getImages());
        ossService.validateOwnedObjectKeys(imageObjectKeys, diary.getUserId());

        List<DiaryAttachmentBinding> bindings = parseAttachmentBindingsForWrite(diary.getAttachmentBindingsJson());
        Set<String> imageKeySet = new HashSet<>(imageObjectKeys);
        for (DiaryAttachmentBinding binding : bindings) {
            if (DiaryAttachmentType.IMAGE.code().equals(binding.getType())) {
                if (!imageKeySet.contains(binding.getObjectKey())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "图片绑定必须引用当前日记附件");
                }
            } else if (DiaryAttachmentType.AUDIO.code().equals(binding.getType())) {
                ossService.validateOwnedAudioObjectKey(binding.getObjectKey(), diary.getUserId());
            }
        }
        diary.setAttachmentBindingsJson(serializeAttachmentBindings(bindings));

        if (StrUtil.isNotBlank(diary.getAudioObjectKey())) {
            ossService.validateOwnedAudioObjectKey(diary.getAudioObjectKey(), diary.getUserId());
        }
    }

    private List<String> parseImageObjectKeys(String imagesJson) {
        if (StrUtil.isBlank(imagesJson)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(imagesJson, String.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日记图片数据不合法");
        }
    }

    private List<DiaryAttachmentBinding> parseAttachmentBindings(String bindingsJson) {
        return parseAttachmentBindings(bindingsJson, false);
    }

    private List<DiaryAttachmentBinding> parseAttachmentBindingsForWrite(String bindingsJson) {
        return parseAttachmentBindings(bindingsJson, true);
    }

    private List<DiaryAttachmentBinding> parseAttachmentBindings(String bindingsJson, boolean rejectLegacyBindings) {
        if (StrUtil.isBlank(bindingsJson)) {
            return List.of();
        }
        final List<DiaryAttachmentBinding> bindings;
        try {
            bindings = JSONUtil.toList(bindingsJson, DiaryAttachmentBinding.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日记附件绑定数据不合法");
        }
        if (bindings == null || bindings.size() > MAX_ATTACHMENT_BINDINGS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日记附件绑定数量超出限制");
        }
        List<DiaryAttachmentBinding> normalized = new ArrayList<>(bindings.size());
        for (int index = 0; index < bindings.size(); index++) {
            DiaryAttachmentBinding binding = bindings.get(index);
            if (binding == null || StrUtil.isBlank(binding.getType())
                    || StrUtil.isBlank(binding.getObjectKey()) || StrUtil.isBlank(binding.getParagraphId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "日记附件绑定数据不完整");
            }
            String type = binding.getType().trim().toUpperCase(Locale.ROOT);
            if (DiaryAttachmentType.fromCode(type) == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持的日记附件类型");
            }
            int sortOrder = binding.getSortOrder() == null ? index : binding.getSortOrder();
            if (sortOrder < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "日记附件排序值不合法");
            }
            DiaryAttachmentAnchor anchor = normalizeAttachmentAnchor(binding.getAnchor());
            if (anchor == null) {
                if (rejectLegacyBindings) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "日记附件必须绑定到选中文字");
                }
                // Existing rows may still contain the removed paragraph-only shape;
                // the migration clears them and reads must not render them.
                continue;
            }
            normalized.add(DiaryAttachmentBinding.builder()
                    .type(type)
                    .objectKey(binding.getObjectKey().trim())
                    .paragraphId(binding.getParagraphId().trim())
                    .sortOrder(sortOrder)
                    .anchor(anchor)
                    .build());
        }
        return normalized;
    }

    private String serializeAttachmentBindings(List<DiaryAttachmentBinding> bindings) {
        return JSONUtil.toJsonStr(bindings.stream()
                .map(binding -> DiaryAttachmentBinding.builder()
                        .type(binding.getType())
                        .objectKey(binding.getObjectKey())
                        .paragraphId(binding.getParagraphId())
                        .sortOrder(binding.getSortOrder())
                        .anchor(copyAttachmentAnchor(binding.getAnchor()))
                        .build())
                .toList());
    }

    private DiaryAttachmentAnchor normalizeAttachmentAnchor(DiaryAttachmentAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        if (StrUtil.isBlank(anchor.getKind())
                || !DiaryAttachmentAnchorKind.TEXT_RANGE.code().equalsIgnoreCase(anchor.getKind())
                || anchor.getStart() == null || anchor.getEnd() == null
                || anchor.getStart() < 0 || anchor.getEnd() <= anchor.getStart()
                || StrUtil.isBlank(anchor.getQuote())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日记附件文字定位数据不合法");
        }
        return DiaryAttachmentAnchor.builder()
                .kind(DiaryAttachmentAnchorKind.TEXT_RANGE.code())
                .start(anchor.getStart())
                .end(anchor.getEnd())
                .quote(anchor.getQuote())
                .prefix(anchor.getPrefix())
                .suffix(anchor.getSuffix())
                .build();
    }

    private DiaryAttachmentAnchor copyAttachmentAnchor(DiaryAttachmentAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        return DiaryAttachmentAnchor.builder()
                .kind(anchor.getKind())
                .start(anchor.getStart())
                .end(anchor.getEnd())
                .quote(anchor.getQuote())
                .prefix(anchor.getPrefix())
                .suffix(anchor.getSuffix())
                .build();
    }

    private String generateAttachmentUrl(DiaryAttachmentBinding binding, String userId) {
        if (DiaryAttachmentType.IMAGE.code().equals(binding.getType())) {
            return ossService.generateOwnedUrl(binding.getObjectKey(), userId);
        }
        return ossService.generateOwnedAudioUrl(binding.getObjectKey(), userId);
    }

    private void publishDiaryEvents(Diary diary, String plainContent, DiaryChangedEvent.Type type) {
        if (diary == null) {
            return;
        }
        eventPublisher.publishEvent(new DiaryChangedEvent(this, diary, type));

        List<String> imageObjectKeys = Collections.emptyList();
        if (StrUtil.isNotBlank(diary.getImages())) {
            try {
                imageObjectKeys = JSONUtil.toList(diary.getImages(), String.class);
            } catch (RuntimeException e) {
                log.warn("日记图片字段不是有效 JSON，跳过图片认知: diaryId={}", diary.getDiaryId());
            }
        }
        MaskResult maskResult = sensitiveDataMaskService.mask(StrUtil.blankToDefault(plainContent, ""));
        String maskedText = maskResult != null ? maskResult.getMaskedText() : null;
        if (StrUtil.isBlank(maskedText) && !imageObjectKeys.isEmpty()) {
            maskedText = "日记包含图片，请结合图片理解。";
        }
        eventPublisher.publishEvent(new DiaryCognitionIngestEvent(this, CognitionIngestCommand.builder()
                .userId(diary.getUserId())
                .sourceType(SourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .maskedText(maskedText)
                .title(diary.getTitle())
                .placeName(diary.getPlaceName())
                .timestamp(diary.getUpdateTime())
                .confidenceHint(1.0)
                .imageObjectKeys(imageObjectKeys)
                .build()));
    }
}
