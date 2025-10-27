package im.bigs.pg.utils.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * RestClient 공통 설정.
 *
 * 모든 외부 HTTP 통신에 사용할 수 있는 RestClient Bean을 제공합니다.
 */
@Configuration
class RestClientConfig(
    @Value("\${pg.test.base-url}")
    private val testBaseUrl: String,
) {

    /**
     * 기본 RestClient Bean.
     *
     * - Connection Timeout: 5초
     * - Read Timeout: 10초
     * - JDK HttpClient 사용 (Spring 6.1+)
     */
    @Bean
    fun restClient(restClientBuilder: RestClient.Builder): RestClient {
        return restClientBuilder
            .baseUrl(testBaseUrl)
            .requestFactory(clientHttpRequestFactory())
            .build()
    }

    /**
     * HTTP 요청 팩토리 설정.
     *
     * JDK 11+ HttpClient 사용.
     */
    private fun clientHttpRequestFactory(): ClientHttpRequestFactory {
        val factory = JdkClientHttpRequestFactory()
        factory.setReadTimeout(Duration.ofSeconds(10))
        return factory
    }
}
