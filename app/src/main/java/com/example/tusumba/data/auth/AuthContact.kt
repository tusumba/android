package com.example.tusumba.data.auth

private const val MaximumEmailLength = 254
private const val MaximumLocalPartLength = 64

private val emailPattern = Regex(
    """[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*@""" +
        """(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+""" +
        """[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"""
)

private val phonePattern = Regex("""\+?(?:[0-9]|\([0-9]+\))(?:-?(?:[0-9]|\([0-9]+\)))*""")

internal fun normalizeAuthContact(value: String): String? {
    val contact = value.trim()
    return if ('@' in contact) normalizeEmail(contact) else normalizePhone(contact)
}

private fun normalizeEmail(email: String): String? {
    if (email.length > MaximumEmailLength ||
        email.indexOf('@') > MaximumLocalPartLength ||
        !emailPattern.matches(email)
    ) {
        return null
    }
    return email.lowercase()
}

private fun normalizePhone(phone: String): String? {
    val compact = phone.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }
    val digits = compact.filter { it in '0'..'9' }
    if (digits.length !in 8..15 || !phonePattern.matches(compact)) return null

    return when {
        compact.startsWith('+') -> if (digits.startsWith('0')) null else "+$digits"
        digits.length == 11 && (digits.startsWith('7') || digits.startsWith('8')) ->
            "+7${digits.drop(1)}"
        else -> digits
    }
}
