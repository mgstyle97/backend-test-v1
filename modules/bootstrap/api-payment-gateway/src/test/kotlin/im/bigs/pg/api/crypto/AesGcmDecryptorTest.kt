package im.bigs.pg.api.crypto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AesGcmDecryptorTest {

    private val decryptor = AesGcmDecryptor()

    /**
     * 테스트를 위한 암호화 헬퍼 메서드.
     * AesGcmDecryptor의 복호화 로직과 동일한 알고리즘 사용.
     */
    private fun encryptPaymentData(
        paymentData: PaymentData,
        apiKey: String,
        ivBase64Url: String
    ): String {
        // 1) JSON 직렬화
        val json = jacksonObjectMapper().writeValueAsString(paymentData)
        val plainBytes = json.toByteArray(Charsets.UTF_8)

        // 2) 키 생성
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
        val keySpec = SecretKeySpec(keyBytes, "AES")

        // 3) IV 디코딩
        val iv = base64UrlDecode(ivBase64Url)
        require(iv.size == 12) { "IV must be 12 bytes" }

        // 4) GCM 암호화
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherBytes = cipher.doFinal(plainBytes)

        // 5) Base64URL 인코딩
        return base64UrlEncode(cipherBytes)
    }

    private fun base64UrlDecode(input: String): ByteArray {
        var s = input
        val pad = (4 - (s.length % 4)) % 4
        s += "=".repeat(pad)
        return Base64.getUrlDecoder().decode(s)
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    @Nested
    @DisplayName("정상적인 복호화")
    inner class SuccessfulDecryption {

        @Test
        @DisplayName("유효한 암호화 데이터를 복호화할 수 있다")
        fun `유효한 암호화 데이터를 복호화할 수 있다`() {
            // Given
            val apiKey = "test-api-key-12345"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { it.toByte() }) // 12바이트 IV

            val originalData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            val encryptedData = encryptPaymentData(originalData, apiKey, ivBase64Url)

            // When
            val decryptedData = decryptor.decryptToPaymentData(encryptedData, apiKey, ivBase64Url)

            // Then
            assertEquals(originalData.cardNumber, decryptedData.cardNumber)
            assertEquals(originalData.birthDate, decryptedData.birthDate)
            assertEquals(originalData.expiry, decryptedData.expiry)
            assertEquals(originalData.password, decryptedData.password)
            assertEquals(originalData.amount, decryptedData.amount)
        }

        @Test
        @DisplayName("하이픈이 포함된 카드번호를 복호화할 수 있다")
        fun `하이픈이 포함된 카드번호를 복호화할 수 있다`() {
            // Given
            val apiKey = "test-api-key-12345"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { it.toByte() })

            val originalData = PaymentData(
                cardNumber = "1234-5678-9012-3456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 50000
            )

            val encryptedData = encryptPaymentData(originalData, apiKey, ivBase64Url)

            // When
            val decryptedData = decryptor.decryptToPaymentData(encryptedData, apiKey, ivBase64Url)

            // Then
            assertEquals("1234-5678-9012-3456", decryptedData.cardNumber)
            assertEquals(50000, decryptedData.amount)
        }

        @Test
        @DisplayName("다양한 금액을 복호화할 수 있다")
        fun `다양한 금액을 복호화할 수 있다`() {
            // Given
            val apiKey = "test-api-key"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { 0xFF.toByte() })

            val testCases = listOf(1, 100, 1000, 10000, 100000, 999999)

            testCases.forEach { amount ->
                val originalData = PaymentData(
                    cardNumber = "1234567890123456",
                    birthDate = "19900101",
                    expiry = "1230",
                    password = "99",
                    amount = amount
                )

                val encryptedData = encryptPaymentData(originalData, apiKey, ivBase64Url)

                // When
                val decryptedData = decryptor.decryptToPaymentData(encryptedData, apiKey, ivBase64Url)

                // Then
                assertEquals(amount, decryptedData.amount, "금액 $amount 복호화 실패")
            }
        }
    }

    @Nested
    @DisplayName("복호화 실패 케이스")
    inner class DecryptionFailure {

        @Test
        @DisplayName("잘못된 API 키로 복호화 시 예외가 발생한다")
        fun `잘못된 API 키로 복호화 시 예외가 발생한다`() {
            // Given
            val correctApiKey = "correct-api-key"
            val wrongApiKey = "wrong-api-key"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { it.toByte() })

            val originalData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            val encryptedData = encryptPaymentData(originalData, correctApiKey, ivBase64Url)

            // When & Then
            assertFailsWith<RuntimeException> {
                decryptor.decryptToPaymentData(encryptedData, wrongApiKey, ivBase64Url)
            }
        }

        @Test
        @DisplayName("잘못된 IV로 복호화 시 예외가 발생한다")
        fun `잘못된 IV로 복호화 시 예외가 발생한다`() {
            // Given
            val apiKey = "test-api-key"
            val correctIv = base64UrlEncode(ByteArray(12) { it.toByte() })
            val wrongIv = base64UrlEncode(ByteArray(12) { (it + 1).toByte() })

            val originalData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            val encryptedData = encryptPaymentData(originalData, apiKey, correctIv)

            // When & Then
            assertFailsWith<RuntimeException> {
                decryptor.decryptToPaymentData(encryptedData, apiKey, wrongIv)
            }
        }

        @Test
        @DisplayName("잘못된 Base64URL 형식의 암호문은 예외가 발생한다")
        fun `잘못된 Base64URL 형식의 암호문은 예외가 발생한다`() {
            // Given
            val apiKey = "test-api-key"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { it.toByte() })
            val invalidEncryptedData = "not-a-valid-base64url!@#$%"

            // When & Then
            assertFailsWith<Exception> {
                decryptor.decryptToPaymentData(invalidEncryptedData, apiKey, ivBase64Url)
            }
        }

        @Test
        @DisplayName("IV 길이가 12바이트가 아닌 경우 예외가 발생한다")
        fun `IV 길이가 12바이트가 아닌 경우 예외가 발생한다`() {
            // Given
            val apiKey = "test-api-key"
            val invalidIv = base64UrlEncode(ByteArray(16) { it.toByte() }) // 16바이트 (잘못된 길이)

            val originalData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            // 올바른 IV로 암호화
            val correctIv = base64UrlEncode(ByteArray(12) { it.toByte() })
            val encryptedData = encryptPaymentData(originalData, apiKey, correctIv)

            // When & Then
            assertFailsWith<IllegalArgumentException> {
                decryptor.decryptToPaymentData(encryptedData, apiKey, invalidIv)
            }
        }

        @Test
        @DisplayName("변조된 암호문은 복호화 시 예외가 발생한다")
        fun `변조된 암호문은 복호화 시 예외가 발생한다`() {
            // Given
            val apiKey = "test-api-key"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { it.toByte() })

            val originalData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            val encryptedData = encryptPaymentData(originalData, apiKey, ivBase64Url)

            // 암호문 변조 (마지막 문자 변경)
            val tamperedData = if (encryptedData.isNotEmpty()) {
                encryptedData.dropLast(1) + "X"
            } else {
                "X"
            }

            // When & Then
            assertFailsWith<RuntimeException> {
                decryptor.decryptToPaymentData(tamperedData, apiKey, ivBase64Url)
            }
        }
    }

    @Nested
    @DisplayName("실제 사용 시나리오")
    inner class RealWorldScenario {

        @Test
        @DisplayName("PG사에서 받은 형식의 데이터를 처리할 수 있다")
        fun `PG사에서 받은 형식의 데이터를 처리할 수 있다`() {
            // Given - 실제 PG사 시나리오 시뮬레이션
            val apiKey = "my-secret-pg-api-key-2024"
            val ivBase64Url = base64UrlEncode("pg-init-vec!".toByteArray().copyOf(12))

            val paymentData = PaymentData(
                cardNumber = "1111-2222-3333-4444",
                birthDate = "19851215",
                expiry = "1228",
                password = "34",
                amount = 123456
            )

            // PG사가 암호화한 데이터라고 가정
            val encryptedByPg = encryptPaymentData(paymentData, apiKey, ivBase64Url)

            // When - 서버에서 복호화
            val decrypted = decryptor.decryptToPaymentData(encryptedByPg, apiKey, ivBase64Url)

            // Then
            assertEquals("1111-2222-3333-4444", decrypted.cardNumber)
            assertEquals("19851215", decrypted.birthDate)
            assertEquals("1228", decrypted.expiry)
            assertEquals("34", decrypted.password)
            assertEquals(123456, decrypted.amount)
        }

        @Test
        @DisplayName("연속된 여러 복호화 요청을 처리할 수 있다")
        fun `연속된 여러 복호화 요청을 처리할 수 있다`() {
            // Given
            val apiKey = "test-api-key"
            val ivBase64Url = base64UrlEncode(ByteArray(12) { it.toByte() })

            val payments = listOf(
                PaymentData("1234567890123456", "19900101", "1230", "12", 1000),
                PaymentData("2345678901234567", "19910202", "1231", "23", 2000),
                PaymentData("3456789012345678", "19920303", "1232", "34", 3000)
            )

            // When
            val results = payments.map { payment ->
                val encrypted = encryptPaymentData(payment, apiKey, ivBase64Url)
                decryptor.decryptToPaymentData(encrypted, apiKey, ivBase64Url)
            }

            // Then
            results.forEachIndexed { index, decrypted ->
                assertEquals(payments[index].cardNumber, decrypted.cardNumber)
                assertEquals(payments[index].amount, decrypted.amount)
            }
        }
    }
}
