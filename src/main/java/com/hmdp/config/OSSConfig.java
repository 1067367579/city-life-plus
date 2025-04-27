package com.hmdp.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.hmdp.oss.OSSProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OSSConfig {

    @Autowired
    private OSSProperties prop;

    public OSS ossClient;

    @Bean
    public OSS ossClient() {
        DefaultCredentialProvider credentialsProvider =
                CredentialsProviderFactory.newDefaultCredentialProvider(
                        prop.getAccessKeyId(), prop.getAccessKeySecret());
        // 创建ClientBuilderConfiguration
        ClientBuilderConfiguration clientBuilderConfiguration = new
                ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        // 使⽤内⽹endpoint进⾏上传
        ossClient = OSSClientBuilder.create()
                .endpoint(prop.getEndpoint())
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(prop.getRegion())
                .build();
        return ossClient;
    }

    @PreDestroy
    public void destroy() {
        ossClient.shutdown();
    }
}
