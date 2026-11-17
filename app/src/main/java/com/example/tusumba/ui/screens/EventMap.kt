package com.example.tusumba.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal fun openEventMap(context: Context, address: String): Boolean {
    if (address.isBlank()) return false
    val encoded = Uri.encode(address.trim())
    val destinations =
        listOf(
            Uri.parse("geo:0,0?q=$encoded"),
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"),
        )
    for (destination in destinations) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, destination))
            return true
        } catch (_: ActivityNotFoundException) {
            // Если карт нет, следующий вариант открывает адрес в браузере.
        } catch (_: SecurityException) {
            continue
        }
    }
    return false
}
