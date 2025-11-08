package im.bigs.pg.api.config.filter

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilterConfig {

    @Bean
    fun traceIdFilterRegistration(): FilterRegistrationBean<LoggingFilter> {
        return FilterRegistrationBean<LoggingFilter>().apply {
            filter = LoggingFilter()
            addUrlPatterns("/*")
            order = 1
        }
    }
}
