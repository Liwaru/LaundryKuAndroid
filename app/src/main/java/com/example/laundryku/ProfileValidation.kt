package com.example.laundryku

object ProfileValidation {
    const val MAX_NAME_LENGTH = 9
    const val MAX_USERNAME_LENGTH = 12
    const val MAX_PHONE_LENGTH = 12
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_PASSWORD_LENGTH = 16

    fun editErrors(name: String, username: String, phone: String): EditErrors = EditErrors(
        name = when {
            name.isBlank() -> InputError.REQUIRED
            name.length > MAX_NAME_LENGTH -> InputError.TOO_LONG
            else -> null
        },
        username = when {
            username.isBlank() -> InputError.REQUIRED
            username.length > MAX_USERNAME_LENGTH -> InputError.TOO_LONG
            else -> null
        },
        phone = when {
            phone.isBlank() -> InputError.REQUIRED
            !phone.matches(Regex("^[0-9]{1,$MAX_PHONE_LENGTH}$")) -> InputError.INVALID
            else -> null
        }
    )

    fun passwordErrors(old: String, new: String, confirmation: String): PasswordErrors = PasswordErrors(
        old = if (old.isEmpty()) InputError.REQUIRED else null,
        new = when {
            new.isEmpty() -> InputError.REQUIRED
            new.length < MIN_PASSWORD_LENGTH -> InputError.TOO_SHORT
            new.length > MAX_PASSWORD_LENGTH -> InputError.TOO_LONG
            new == old -> InputError.SAME_AS_OLD
            else -> null
        },
        confirmation = when {
            confirmation.isEmpty() -> InputError.REQUIRED
            confirmation != new -> InputError.MISMATCH
            else -> null
        }
    )
}

enum class InputError { REQUIRED, TOO_SHORT, TOO_LONG, INVALID, MISMATCH, SAME_AS_OLD }

data class EditErrors(val name: InputError?, val username: InputError?, val phone: InputError?) {
    val isValid: Boolean get() = name == null && username == null && phone == null
}

data class PasswordErrors(val old: InputError?, val new: InputError?, val confirmation: InputError?) {
    val isValid: Boolean get() = old == null && new == null && confirmation == null
}
