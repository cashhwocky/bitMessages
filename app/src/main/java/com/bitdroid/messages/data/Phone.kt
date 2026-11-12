package com.bitdroid.messages.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import java.util.Locale

object Phone {
    /** Strips MMS address decorations like "/TYPE=PLMN" and whitespace. */
    fun clean(address: String): String =
        address.substringBefore("/TYPE=").trim()

    fun isEmail(address: String) = address.contains('@')

    fun normalize(address: String): String {
        val a = clean(address)
        if (isEmail(a)) return a.lowercase(Locale.ROOT)
        return PhoneNumberUtils.normalizeNumber(a).ifEmpty { a }
    }

    /** Loose equality that tolerates country-code differences (last 7+ digits must match). */
    fun same(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return true
        if (isEmail(na) || isEmail(nb)) return false
        val da = na.filter { it.isDigit() }
        val db = nb.filter { it.isDigit() }
        if (da.length < 7 || db.length < 7) return false
        val n = minOf(da.length, db.length, 10)
        return da.takeLast(n) == db.takeLast(n)
    }

    fun format(context: Context, address: String): String {
        val a = clean(address)
        if (isEmail(a)) return a
        val country = context.resources.configuration.locales[0].country.ifEmpty { "US" }
        return PhoneNumberUtils.formatNumber(a, country) ?: a
    }
}
