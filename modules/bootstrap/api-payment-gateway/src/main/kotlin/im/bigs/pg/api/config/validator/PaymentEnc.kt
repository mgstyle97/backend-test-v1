package im.bigs.pg.api.config.validator

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [PaymentEncValidator::class])
annotation class PaymentEnc(
    val message: String = "잘못된 형식의 결제 암호화 데이터입니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
