//package com.project.log_layer.integration.config;
//
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.reactive.function.client.WebClient;
//
//@Configuration
//@EnableConfigurationProperties(MlServiceProperties.class)
//public class WebClientConfig {
//
//    @Bean
//    public WebClient mlWebClient(
//            WebClient.Builder builder,
//            MlServiceProperties properties
//    ) {
//
//        return builder
//                .baseUrl(properties.getBaseUrl())
//                .build();
//    }
//}

package com.project.log_layer.integration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({
        MlServiceProperties.class,
        LlmServiceProperties.class
})
public class WebClientConfig {

    @Bean
    public WebClient mlWebClient(
            WebClient.Builder builder,
            MlServiceProperties properties
    ) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Bean
    public WebClient llmWebClient(
            WebClient.Builder builder,
            LlmServiceProperties properties
    ) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
