//package hackerton.seosancheck.common.config;
//
//import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
//import org.apache.hc.client5.http.impl.classic.HttpClients;
//import org.springframework.boot.web.client.RestTemplateBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.Duration;
//
//@Configuration
//public class RestTemplateConfig {
//
//    @Bean
//    public RestTemplate restTemplate(RestTemplateBuilder builder) {
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//
//        return builder
//                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
//                .setConnectTimeout(Duration.ofSeconds(4))
//                .setReadTimeout(Duration.ofSeconds(8))
//                .build();
//    }
//}
