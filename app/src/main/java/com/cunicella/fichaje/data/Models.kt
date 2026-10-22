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
)
