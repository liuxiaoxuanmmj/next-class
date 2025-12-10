package edu.zzttc.backend.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;

@Data
@Slf4j
@Component
public class AliOssUtil {

    @Value("${zzttc.alioss.endpoint}")
    private String endpoint;

    @Value("${zzttc.alioss.access-key-id}")
    private String accessKeyId;

    @Value("${zzttc.alioss.access-key-secret}")
    private String accessKeySecret;

    @Value("${zzttc.alioss.bucket-name}")
    private String bucketName;

    /**
     * 上传文件到 OSS
     * @param bytes 文件字节数组
     * @param objectName 存储对象名（例： "banner/flower1.jpg"）
     * @return 上传结果对象，包含文件 URL、大小、类型
     */
    public OssUploadResult upload(byte[] bytes, String objectName) {
        OSS ossClient = null;
        try {
            // 自动识别 MIME 类型（防止图片下载）
            String mimeType = URLConnection.guessContentTypeFromName(objectName);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            log.info("准备上传文件至 OSS：objectName={}, mimeType={}", objectName, mimeType);

            // 创建 OSS 客户端
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 设置对象元数据（防止浏览器下载）
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(mimeType);

            // 上传文件
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes), metadata);

            // 拼接访问 URL
            String fileUrl = String.format("https://%s.%s/%s", bucketName, endpoint, objectName);
            log.info("文件上传成功，访问地址：{}", fileUrl);

            return new OssUploadResult(fileUrl, bytes.length, mimeType);

        } catch (OSSException oe) {
            log.error("OSS 服务端异常：Code={}, Message={}, RequestId={}, HostId={}",
                    oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId(), oe.getHostId(), oe);
            throw new RuntimeException("OSS 服务端上传失败，请稍后重试");
        } catch (ClientException ce) {
            log.error("OSS 客户端异常：{}", ce.getMessage(), ce);
            throw new RuntimeException("OSS 客户端上传失败，请检查网络连接");
        } catch (Exception e) {
            log.error("文件上传未知错误：{}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败，请联系管理员");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
                log.debug("OSS 客户端连接已关闭");
            }
        }
    }

    /**
     * 上传结果封装
     */
    @Data
    @AllArgsConstructor
    public static class OssUploadResult {
        private String url;
        private long size;
        private String mimeType;
    }
}
