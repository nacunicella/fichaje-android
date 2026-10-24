package com.cunicella.fichaje.data

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val company: String,
    val currency: String = "ARS",
    val rate: Double,
    @SerialName("holiday_mult") val holidayMult: Double = 2.0,
    @SerialName("goal_hours") val goalHours: Double = 8.0,
)

@Serializable
data class Member(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String? = null,
    val rate: Double? = null,
    val active: Boolean = true,
    val role: String = "member",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("deleted_at") val deletedAt: Instant? = null,
) {
    val name: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Sin nombre" }
    val shortName: String get() = firstName.ifBlank { "Sin nombre" }
    val initials: String get() = if (name == "Sin nombre") "?" else name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }
    val isAdmin: Boolean get() = role == "admin"
}

@Serializable
data class Pause(
    val id: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("start_at") val startAt: Instant,
    @SerialName("end_at") val endAt: Instant? = null,
)

@Serializable
data class Shift(
    val id: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("start_at") val startAt: Instant,
    @SerialName("end_at") val endAt: Instant? = null,
    val hours: Double? = null,
    val holiday: Boolean = false,
    val manual: Boolean = false,
    val note: String? = null,
    val rate: Double? = null,
    @SerialName("payment_id") val paymentId: String? = null,
    @SerialName("deleted_at") val deletedAt: Instant? = null,
    val pauses: List<Pause> = emptyList(),
)

@Serializable
data class NewShift(
    @SerialName("member_id") val memberId: String,
    @SerialName("start_at") val startAt: Instant,
    val holiday: Boolean = false,
)

@Serializable
data class NewPause(
    @SerialName("shift_id") val shiftId: String,
    @SerialName("start_at") val startAt: Instant,
    @SerialName("end_at") val endAt: Instant? = null,
)

@Serializable
data class Payment(
    val id: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("paid_at") val paidAt: Instant,
    val amount: Double,
    @SerialName("worked_seconds") val workedSeconds: Long,
    @SerialName("shift_count") val shiftCount: Int,
    @SerialName("deleted_at") val deletedAt: Instant? = null,
)

@Serializable
data class NewPayment(
    @SerialName("member_id") val memberId: String,
    val amount: Double,
    @SerialName("worked_seconds") val workedSeconds: Long,
    @SerialName("shift_count") val shiftCount: Int,
)

/** Jornada cargada a mano, por horario o por horas. */
@Serializable
data class ManualShift(
    @SerialName("member_id") val memberId: String,
    @SerialName("start_at") val startAt: Instant,
    @SerialName("end_at") val endAt: Instant,
    val hours: Double? = null,
    val holiday: Boolean = false,
    val manual: Boolean = true,
    val note: String? = null,
    val rate: Double,
)

@Serializable
data class NewMember(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val email: String?,
    val rate: Double?,
    val active: Boolean,
    val role: String,
)

/** Una fila de la auditoría tal como la escribe el trigger. */
@Serializable
data class AuditRow(
    val id: Long,
    val at: Instant,
    @SerialName("actor_user") val actorUser: String? = null,
    @SerialName("actor_member") val actorMember: String? = null,
    @SerialName("table_name") val table: String,
    val action: String,
    @SerialName("row_id") val rowId: String? = null,
    @SerialName("member_id") val memberId: String? = null,
    @SerialName("old_data") val old: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("new_data") val new: kotlinx.serialization.json.JsonObject? = null,
)
