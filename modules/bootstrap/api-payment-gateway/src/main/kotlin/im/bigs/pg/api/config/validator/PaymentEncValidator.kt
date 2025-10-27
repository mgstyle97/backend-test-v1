package im.bigs.pg.api.config.validator

import im.bigs.pg.api.crypto.AesGcmDecryptor
import im.bigs.pg.api.crypto.PaymentData
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.stereotype.Component

@Component
class PaymentEncValidator(
    private val decryptor: AesGcmDecryptor = AesGcmDecryptor(),
    private val properties: TestPgProperties,
) : ConstraintValidator<PaymentEnc, String> {
    override fun isValid(p0: String, p1: ConstraintValidatorContext?): Boolean {
        try {
            val decryptedEnc: PaymentData = decryptor.decryptToPaymentData(p0, properties.apiKey, properties.iv)

            validateCardNumber(decryptedEnc.cardNumber)
            validateBirthDate(decryptedEnc.birthDate)
            validateExpiry(decryptedEnc.expiry)
            validatePassword(decryptedEnc.password)
            validateAmount(decryptedEnc.amount)

            return true
        } catch (e: RuntimeException) {
            return false
        }
    }

    // 1. 카드번호 검증 (16자리 숫자, 하이픈(-) 허용)
    private fun validateCardNumber(cardNumber: String) {
        val digitsOnly = cardNumber.replace("-", "")
        if (!digitsOnly.matches(Regex("^\\d{16}$"))) {
            throw IllegalArgumentException("카드번호는 숫자 16자리여야 하며, 하이픈(-)만 허용됩니다.")
        }
    }

    // 2. 생년월일 검증 (YYYYMMDD, 실제 존재하는 날짜여야 함)
    private fun validateBirthDate(birthDate: String) {
        if (!birthDate.matches(Regex("^\\d{8}$"))) {
            throw IllegalArgumentException("생년월일은 YYYYMMDD 형식이어야 합니다.")
        }

        try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            java.time.LocalDate.parse(birthDate, formatter)
        } catch (e: Exception) {
            throw IllegalArgumentException("유효하지 않은 날짜 형식입니다.")
        }
    }

    // 3. 만료일 검증 (MMYY)
    private fun validateExpiry(expiry: String) {
        if (!expiry.matches(Regex("^\\d{4}$"))) {
            throw IllegalArgumentException("만료일은 MMYY 형식이어야 합니다.")
        }

        val month = expiry.substring(0, 2).toIntOrNull() ?: throw IllegalArgumentException("유효하지 않은 월 형식입니다.")
        val year = expiry.substring(2, 4).toIntOrNull() ?: throw IllegalArgumentException("유효하지 않은 년도 형식입니다.")

        if (month !in 1..12) throw IllegalArgumentException("유효하지 않은 월입니다 (01~12).")

        // 현재 시점 이후인지 체크 (선택적)
        val now = java.time.YearMonth.now()
        val cardExpiry = java.time.YearMonth.of(2000 + year, month)
        if (cardExpiry.isBefore(now)) {
            throw IllegalArgumentException("카드 만료일이 지났습니다.")
        }
    }

    // 4. 카드 비밀번호 앞 2자리 검증
    private fun validatePassword(password: String) {
        if (!password.matches(Regex("^\\d{2}$"))) {
            throw IllegalArgumentException("비밀번호는 숫자 2자리여야 합니다.")
        }
    }

    // 5. 금액 검증 (1 이상 정수)
    private fun validateAmount(amount: Int) {
        if (amount < 1) {
            throw IllegalArgumentException("결제 금액은 1원 이상이어야 합니다.")
        }
    }
}
