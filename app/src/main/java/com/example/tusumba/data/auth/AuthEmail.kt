package com.example.tusumba.data.auth

private const val MaximumEmailLength = 254
private const val MaximumLocalPartLength = 64

private val emailPattern = Regex(
    """[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*@""" +
        """(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+""" +
        """[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"""
)

/** Email из RegisterRequest: не длиннее 254 символов, уникален без учёта регистра. */
internal fun normalizeEmail(value: String): String? {
    val email = value.trim()
    if (email.length > MaximumEmailLength ||
        email.indexOf('@') > MaximumLocalPartLength ||
        !emailPattern.matches(email)
    ) {
        return null
    }
    return email.lowercase()
}
