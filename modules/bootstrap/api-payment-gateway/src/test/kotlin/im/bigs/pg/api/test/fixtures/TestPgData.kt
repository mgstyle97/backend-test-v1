package im.bigs.pg.api.test.fixtures

/**
 * TestPG 서버 테스트용 암호화 데이터
 *
 * TestPG 서버는 특정 암호문만 승인 처리합니다.
 * 이 파일은 테스트에 필요한 승인용 암호문을 관리합니다.
 *
 * @see https://api-test-pg.bigs.im/docs/index.html
 */
object TestPgData {

    /**
     * 10,000원 승인용 암호문
     *
     * 평문 내용:
     * - cardNumber: 1111-1111-1111-1111
     * - birthDate: 19900101
     * - expiry: 1227
     * - password: 12
     * - amount: 10000
     *
     * TestPG 서버에서 제공하는 공식 테스트 암호문입니다.
     */
    const val ENC_APPROVED_10000 =
        "FlrQ_ZFCA9WC7HIkPzKFpnzv1AX0n7zodWtWRo6X6-ccrzwEkwOGAJyfC4cwihNy4EtwXS6yx2FBHcOP44mxDAZvv38YF6LLnBSBW2zpsvBUgImnuR6Gc_z1CTID_tuA-Rpmrhjoguyl3PxnF9A5dhTLM6T0HO4JxbA"

    /**
     * 평문 데이터 (참고용)
     * 실제 암호화는 TestPG 서버에서 제공하는 암호문을 사용합니다.
     */
    data class PlainData(
        val cardNumber: String,
        val birthDate: String,
        val expiry: String,
        val password: String,
        val amount: Int,
        val description: String = ""
    )

    /**
     * 10,000원 결제 평문 데이터 (참고용)
     */
    val PLAIN_10000 = PlainData(
        cardNumber = "1234567890123456",
        birthDate = "19900101",
        expiry = "1230",
        password = "12",
        amount = 10000,
        description = "TestPG 승인 테스트용 10,000원 결제"
    )

    /**
     * 다양한 금액의 암호문들
     * TestPG 문서에서 추가 암호문을 제공하면 여기에 추가합니다.
     */

    // 필요시 추가:
    // const val ENC_APPROVED_50000 = "..."
    // const val ENC_APPROVED_100000 = "..."
    // const val ENC_APPROVED_1000000 = "..."

    // const val ENC_REJECTED = "..." // 승인 거절용
}
