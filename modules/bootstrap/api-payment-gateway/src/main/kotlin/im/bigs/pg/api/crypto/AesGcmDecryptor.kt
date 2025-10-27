package im.bigs.pg.api.crypto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.util.*
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmDecryptor {

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    // Base64URL 문자열을 안전하게 디코드 (패딩 없을 수 있으니 보정)
    private fun base64UrlDecode(input: String): ByteArray {
        var s = input
            .replace('-', '+') // URL-safe -> standard (optional)
            .replace('_', '/')
        val pad = (4 - (s.length % 4)) % 4
        s += "=".repeat(pad)
        return Base64.getDecoder().decode(s)
    }

    // 또는 URL 디코더 직접 사용(대부분 패딩 없이도 동작하지만 위 보정 함수가 안전)
    private fun base64UrlDecodeUsingUrlDecoder(input: String): ByteArray {
        var s = input
        val pad = (4 - (s.length % 4)) % 4
        s += "=".repeat(pad)
        return Base64.getUrlDecoder().decode(s)
    }

    /**
     * encBase64Url: 서버에서 받은 enc 값 (Base64URL, 패딩 없음)
     * apiKey: API-KEY (원문)
     * ivBase64Url: IV (Base64URL, 12바이트가 되도록 서버에 등록된 값)
     *
     * 반환: PaymentData 객체 (복호화 실패 시 예외 throw)
     */
    fun decryptToPaymentData(encBase64Url: String, apiKey: String, ivBase64Url: String): PaymentData {
        try {
            // 1) 준비: 키, iv, 암호문 바이트
            val keyBytes = sha256(apiKey) // 32바이트 AES-256 키
            val keySpec = SecretKeySpec(keyBytes, "AES")

            val iv = base64UrlDecodeUsingUrlDecoder(ivBase64Url)
            require(iv.size == 12) { "IV must be 12 bytes (Base64URL decode 결과). 현재 길이: ${iv.size}" }

            val cipherBytes = base64UrlDecodeUsingUrlDecoder(encBase64Url)

            // 2) GCM 복호화 (태그 길이 128비트)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val plainBytes = cipher.doFinal(cipherBytes) // 여기서 AEAD 인증 검사도 수행됨
            val json = String(plainBytes, Charsets.UTF_8)

            // 3) JSON -> 데이터 클래스
            val mapper = jacksonObjectMapper()
            return mapper.readValue(json)
        } catch (expection: AEADBadTagException) {
            throw RuntimeException("Failed to decrypt payment data", expection)
        }
    }
}

data class PaymentData(
    val cardNumber: String,
    val birthDate: String,
    val expiry: String,
    val password: String,
    val amount: Int
)
