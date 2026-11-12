package com.bitdroid.messages.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager
import android.provider.ContactsContract
import java.util.concurrent.ConcurrentHashMap

object Contacts {
    private val cache = ConcurrentHashMap<String, Contact>()

    fun clearCache() = cache.clear()

    private fun canRead(context: Context): Boolean {
        val um = context.getSystemService(UserManager::class.java)
        if (um != null && !um.isUserUnlocked) return false
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun lookup(context: Context, rawAddress: String): Contact {
        val address = Phone.clean(rawAddress)
        cache[address]?.let { return it }
        var contact = Contact(address, null, null, null)
        if (canRead(context) && address.isNotBlank()) {
            try {
                if (Phone.isEmail(address)) {
                    val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(address))
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.Data.CONTACT_ID,
                            ContactsContract.Data.DISPLAY_NAME,
                            ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                            ContactsContract.Data.LOOKUP_KEY,
                        ),
                        null, null, null,
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            contact = Contact(
                                address,
                                c.getString(1),
                                c.getString(2)?.let(Uri::parse),
                                ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(3)),
                            )
                        }
                    }
                } else {
                    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.PhoneLookup._ID,
                            ContactsContract.PhoneLookup.DISPLAY_NAME,
                            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                            ContactsContract.PhoneLookup.LOOKUP_KEY,
                        ),
                        null, null, null,
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            contact = Contact(
                                address,
                                c.getString(1),
                                c.getString(2)?.let(Uri::parse),
                                ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(3)),
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
            cache[address] = contact
        }
        if (contact.name == null) contact = contact.copy(address = address)
        return contact
    }

    data class PhoneEntry(val name: String, val number: String, val label: String, val photo: Uri?)

    /** Contacts with phone numbers matching [query] (name or number), for the new-conversation picker. */
    fun search(context: Context, query: String, limit: Int = 50): List<PhoneEntry> {
        if (!canRead(context)) return emptyList()
        val uri = if (query.isBlank()) {
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        } else {
            Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        }
        val out = ArrayList<PhoneEntry>()
        val seen = HashSet<String>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(1) ?: continue
                    val key = (c.getString(0) ?: "") + Phone.normalize(number)
                    if (!seen.add(key)) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources, c.getInt(2), c.getString(3),
                    ).toString()
                    out += PhoneEntry(c.getString(0) ?: number, number, label, c.getString(4)?.let(Uri::parse))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun contactIdUri(id: Long): Uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
}
