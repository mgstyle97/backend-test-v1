package im.bigs.pg.api.config.validator

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "pg.crypto")
data class TestPgProperties(
    var apiKey: String = "",
    var iv: String = "",
)
