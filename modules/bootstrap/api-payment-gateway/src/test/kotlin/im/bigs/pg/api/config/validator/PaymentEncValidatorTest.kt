package im.bigs.pg.api.config.validator

import im.bigs.pg.api.crypto.AesGcmDecryptor
import im.bigs.pg.api.crypto.PaymentData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentEncValidatorTest {

    private lateinit var decryptor: AesGcmDecryptor
    private lateinit var properties: TestPgProperties
    private lateinit var validator: PaymentEncValidator

    @BeforeEach
    fun setUp() {
        decryptor = mockk()
        properties = TestPgProperties(apiKey = "test-api-key", iv = "test-iv")
        validator = PaymentEncValidator(decryptor, properties)
    }

    @Nested
    @DisplayName("유효한 데이터 검증")
    inner class ValidDataTest {

        @Test
        @DisplayName("모든 필드가 유효한 경우 true를 반환한다")
        fun `모든 필드가 유효한 경우 true를 반환한다`() {
            // Given
            val encryptedData = "valid-encrypted-data"
            val validPaymentData = PaymentData(
                cardNumber = "1234-5678-9012-3456",
                birthDate = "19900101",
                expiry = "1230", // 2030년 12월
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns validPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertTrue(result)
            verify(exactly = 1) {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            }
        }

        @Test
        @DisplayName("카드번호에 하이픈이 없어도 유효하다")
        fun `카드번호에 하이픈이 없어도 유효하다`() {
            // Given
            val encryptedData = "valid-encrypted-data"
            val validPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns validPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("카드번호 검증 실패")
    inner class CardNumberValidationTest {

        @Test
        @DisplayName("카드번호가 16자리가 아닌 경우 false를 반환한다")
        fun `카드번호가 16자리가 아닌 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234-5678-9012", // 12자리
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("카드번호에 숫자가 아닌 문자가 포함된 경우 false를 반환한다")
        fun `카드번호에 숫자가 아닌 문자가 포함된 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234-5678-90AB-3456", // 알파벳 포함
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("생년월일 검증 실패")
    inner class BirthDateValidationTest {

        @Test
        @DisplayName("생년월일 형식이 YYYYMMDD가 아닌 경우 false를 반환한다")
        fun `생년월일 형식이 YYYYMMDD가 아닌 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "1990-01-01", // 하이픈 포함
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("유효하지 않은 날짜인 경우 false를 반환한다")
        fun `유효하지 않은 날짜인 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19901332", // 13월 32일
                expiry = "1230",
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("만료일 검증 실패")
    inner class ExpiryValidationTest {

        @Test
        @DisplayName("만료일 형식이 MMYY가 아닌 경우 false를 반환한다")
        fun `만료일 형식이 MMYY가 아닌 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "12/30", // 슬래시 포함
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("유효하지 않은 월인 경우 false를 반환한다")
        fun `유효하지 않은 월인 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1330", // 13월
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("만료일이 과거인 경우 false를 반환한다")
        fun `만료일이 과거인 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "0120", // 2020년 1월 (과거)
                password = "12",
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("비밀번호 검증 실패")
    inner class PasswordValidationTest {

        @Test
        @DisplayName("비밀번호가 2자리가 아닌 경우 false를 반환한다")
        fun `비밀번호가 2자리가 아닌 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "1234", // 4자리
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("비밀번호가 숫자가 아닌 경우 false를 반환한다")
        fun `비밀번호가 숫자가 아닌 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "AB", // 알파벳
                amount = 10000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("금액 검증 실패")
    inner class AmountValidationTest {

        @Test
        @DisplayName("금액이 1원 미만인 경우 false를 반환한다")
        fun `금액이 1원 미만인 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = 0
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("금액이 음수인 경우 false를 반환한다")
        fun `금액이 음수인 경우 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"
            val invalidPaymentData = PaymentData(
                cardNumber = "1234567890123456",
                birthDate = "19900101",
                expiry = "1230",
                password = "12",
                amount = -1000
            )

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } returns invalidPaymentData

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("복호화 실패")
    inner class DecryptionFailureTest {

        @Test
        @DisplayName("복호화 실패 시 false를 반환한다")
        fun `복호화 실패 시 false를 반환한다`() {
            // Given
            val encryptedData = "invalid-encrypted-data"

            every {
                decryptor.decryptToPaymentData(encryptedData, properties.apiKey, properties.iv)
            } throws RuntimeException("Decryption failed")

            // When
            val result = validator.isValid(encryptedData, null)

            // Then
            assertFalse(result)
        }
    }
}
