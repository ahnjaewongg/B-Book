package com.bbook.config;

import com.bbook.client.IamportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class IamportConfig {

    @Value("${iamport.key}")
    private String apiKey;

    @Value("${iamport.secret}")
    private String apiSecret;

    /**
     * HTTP 통신을 위한 RestTemplate Bean 생성
     * 아임포트 API와의 HTTP 통신에 사용됨
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 아임포트 결제 API와 통신하기 위한 클라이언트 Bean 생성
     * 
     * restTemplate HTTP 통신을 위한 RestTemplate 객체
     * 
     * @return 아임포트 API 클라이언트 객체
     */
    @Bean
    public IamportClient iamportClient() {
        return new IamportClient(apiKey, apiSecret, restTemplate());
    }
}