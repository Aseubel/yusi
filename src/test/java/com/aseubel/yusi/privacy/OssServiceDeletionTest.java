package com.aseubel.yusi.privacy;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import com.aseubel.yusi.config.oss.OssProperties;
import com.aseubel.yusi.repository.ImageFileRepository;
import com.aseubel.yusi.service.oss.OssService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssServiceDeletionTest {

    private static final String TARGET_USER = "fixture-user-delete-target";
    private static final String ORPHAN_KEY = "images/fixture-user-delete-target/orphan.png";
    private static final String SHARED_KEY = "images/fixture-user-delete-target/shared.png";

    @Test
    void deletesUnmappedObjectsFromOwnedPrefixButRetainsSharedReferences() {
        OSSClient ossClient = mock(OSSClient.class);
        ListObjectsV2Result page = mock(ListObjectsV2Result.class);
        ImageFileRepository imageFileRepository = mock(ImageFileRepository.class);
        OssProperties properties = new OssProperties();
        properties.setBucketName("fixture-bucket");
        properties.setImageFolder("images/");

        when(page.contents()).thenReturn(List.of(
                ObjectSummary.newBuilder().key(ORPHAN_KEY).build(),
                ObjectSummary.newBuilder().key(SHARED_KEY).build()));
        when(page.isTruncated()).thenReturn(false);
        when(imageFileRepository.existsByObjectKeyAndUserIdNot(SHARED_KEY, TARGET_USER))
                .thenReturn(true);
        when(ossClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page);

        OssService service = new OssService(ossClient, properties,
                mock(StringRedisTemplate.class), imageFileRepository);

        service.deleteOwnedImagePrefix(TARGET_USER);

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(ossClient).listObjectsV2(listCaptor.capture());
        assertEquals("fixture-bucket", listCaptor.getValue().bucket());
        assertEquals("images/" + TARGET_USER + "/", listCaptor.getValue().prefix());

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(ossClient, times(1)).deleteObject(deleteCaptor.capture());
        assertEquals(ORPHAN_KEY, deleteCaptor.getValue().key());
    }
}
