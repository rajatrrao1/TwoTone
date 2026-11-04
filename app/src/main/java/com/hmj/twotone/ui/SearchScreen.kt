package com.hmj.twotone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One thing search found: what it is, its name, and what tapping it does. */
class Found(val kind: String, val title: String, val open: () -> Unit)

/** Contacts whose name contains [q] (only if contacts may be read). Call off the main thread. */
fun searchContacts(ctx: android.content.Context, q: String): List<Pair<String, String>> {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    try {
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c -> while (c.moveToNext() && out.size < 20) out += (c.getString(0) ?: "") to (c.getString(1) ?: "") }
    } catch (e: Exception) { }
    return out.distinctBy { it.first }
}

/**
 * One find box for everything: apps, contacts, notes, songs, books, saved news.
 * [search] does the looking (it knows where everything lives); results come
 * grouped by kind, and Enter opens the first one.
 */
@Composable
fun SearchScreen(search: suspend (String) -> List<Found>, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val q = query.trim()
    val results by produceState(emptyList<Found>(), q) {
        if (q.isEmpty()) { value = emptyList(); return@produceState }
        delay(250)  // wait for a pause in typing
        value = withContext(Dispatchers.IO) { search(q) }
    }
    PageColumn {
        PageHeader("search", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("find: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            BasicTextField(
                value = query, onValueChange = { query = it.replace("\n", "") }, singleLine = true,
                textStyle = textStyle(18f), cursorBrush = SolidColor(palette.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.open?.invoke() }),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        SolidRule(thickness = 3.dp)
        if (q.isEmpty()) {
            Hint("Apps, contacts, notes, songs, books and saved news.")
            Spacer(Modifier.weight(1f))
        } else if (results.isEmpty()) {
            Hint("Nothing found for \u201c$q\u201d.")
            Spacer(Modifier.weight(1f))
        } else {
            PagedList(results, Modifier.weight(1f), resetKey = q to results.size) { f ->
                ListRow(f.title, trailing = f.kind, onPress = f.open)
            }
        }
        FooterLine(if (q.isEmpty()) "swipe down on the home list to come here" else "${results.size} found · enter opens the first")
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
