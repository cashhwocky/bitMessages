package com.bitdroid.messages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.messages.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

// ------------------------------------------------------------------ images

private val bitmapCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

fun loadBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val key = "$uri@$maxDim"
    bitmapCache.get(key)?.let { return it }
    return try {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val s = minOf(1f, maxDim.toFloat() / maxOf(w, h))
            if (s < 1f) d.setTargetSize(maxOf(1, (w * s).toInt()), maxOf(1, (h * s).toInt()))
        }
        bitmapCache.put(key, bmp)
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
fun rememberImage(uri: Uri?, maxDim: Int): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(null, uri, maxDim) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { loadBitmap(context, uri, maxDim)?.asImageBitmap() }
    }
    return state
}

// ----------------------------------------------------------------- avatars

private val avatarColors = listOf(
    Color(0xFF5B3FA0), Color(0xFF7A3550), Color(0xFF2B5E8C), Color(0xFF2F6B3A),
    Color(0xFF8A4F68), Color(0xFF9A4A26), Color(0xFF4E5070), Color(0xFF1F6A72),
)

/** bitHome pastels: fill and letter color. */
private val pastelColors = listOf(
    Color(0xFFCBC2EB) to Color(0xFF241C4A), Color(0xFFF6D3EA) to Color(0xFF4A1E3E), Color(0xFFC7E8DF) to Color(0xFF123D34),
    Color(0xFFFFDDB8) to Color(0xFF4A2A08), Color(0xFFD6E3FF) to Color(0xFF0E2C5A), Color(0xFFE5DEFF) to Color(0xFF2B2060),
)

@Composable
fun Avatar(contacts: List<Contact>, size: Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val single = contacts.singleOrNull()
    val photo = rememberImage(single?.photoUri, 256)
    val seed = (single?.address ?: contacts.joinToString { it.address }).hashCode()
    val pastel = pastelColors[Math.floorMod(seed, pastelColors.size)]
    val bg = if (Bit.expressive) pastel.first else avatarColors[Math.floorMod(seed, avatarColors.size)]
    val fg = if (Bit.expressive) pastel.second else Color.White
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (single != null && single.name == null) Bit.SurfaceHigh else bg),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(photo, null, Modifier.size(size), contentScale = ContentScale.Crop)
            contacts.size > 1 -> Icon(Icons.Rounded.Group, null, tint = if (Bit.expressive) fg else Bit.Lilac, modifier = Modifier.size(size * 0.5f))
            single?.name != null -> Text(
                single.name.trim().firstOrNull()?.uppercase() ?: "#",
                color = fg,
                fontFamily = if (Bit.expressive) Body else Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.38f).sp,
            )
            else -> Icon(Icons.Rounded.Person, null, tint = Bit.TextDim, modifier = Modifier.size(size * 0.55f))
        }
    }
}

// -------------------------------------------------------------- search pill

@Composable
fun SearchPill(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search messages",
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(Bit.Surface)
            .border(2.5.dp, Color.White, CircleShape)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = Bit.TextDim, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Bit.Lilac),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Rounded.Close, "Clear", tint = Color.White)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

// ------------------------------------------------------------------- times

fun shortTime(context: Context, millis: Long): String {
    val now = System.currentTimeMillis()
    return when {
        now - millis < DateUtils.MINUTE_IN_MILLIS -> "Now"
        DateUtils.isToday(millis) -> DateFormat.getTimeFormat(context).format(millis)
        now - millis < 6 * DateUtils.DAY_IN_MILLIS -> DateFormat.format("EEE", millis).toString()
        sameYear(now, millis) -> DateFormat.format("MMM d", millis).toString()
        else -> DateFormat.getDateFormat(context).format(millis)
    }
}

fun dayHeader(context: Context, millis: Long): String {
    val time = DateFormat.getTimeFormat(context).format(millis)
    return when {
        DateUtils.isToday(millis) -> "Today · $time"
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday · $time"
        System.currentTimeMillis() - millis < 6 * DateUtils.DAY_IN_MILLIS -> DateFormat.format("EEEE", millis).toString() + " · $time"
        else -> DateFormat.format("EEE, MMM d", millis).toString() + " · $time"
    }
}

private fun sameYear(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
}
