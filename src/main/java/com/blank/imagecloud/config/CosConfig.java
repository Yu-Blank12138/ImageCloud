package com.blank.imagecloud.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按腾讯云官方初始化流程配置 COS 客户端，由 Spring 管理单例及连接池生命周期。
 */
@Getter
@Setter
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "cos")
public class CosConfig {

    /**
     * 腾讯云访问密钥 ID，对应配置项 cos.secret-id。
     */
    private String secretId;

    /**
     * 腾讯云访问密钥 Key，对应配置项 cos.secret-key。
     */
    private String secretKey;

    /**
     * 存储桶所在地域，例如 ap-guangzhou，对应配置项 cos.region。
     */
    private String region;

    /**
     * 使用配置中的密钥和存储桶地域创建 HTTPS 客户端，容器关闭时释放连接池。
     *
     * @return 可复用的 COS 客户端单例
     */
    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient() {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(credentials, clientConfig);
    }
}
