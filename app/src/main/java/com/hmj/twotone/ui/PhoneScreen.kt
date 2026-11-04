package com.hmj.twotone.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Contact(val name: String, val number: String, val t9: String, val digits: String)

private val KEYS = listOf(
    listOf("1" to "", "2" to "abc", "3" to "def"),
    listOf("4" to "ghi", "5" to "jkl", "6" to "mno"),
    listOf("7" to "pqrs", "8" to "tuv", "9" to "wxyz"),
    listOf("*" to "", "0" to "+", "#" to "")
)

/** Letters → keypad digits, so typing 5-2-6 finds "Jan". */
private fun toT9(name: String): String = buildString {
    name.lowercase().forEach { ch ->
        val d = KEYS.flatten().firstOrNull { (_, letters) -> ch in letters && letters != "+" }?.first
        append(d ?: if (ch.isDigit()) ch.toString() else " ")
    }
}

/**
 * Contacts on top, keypad underneath. Typing on the keypad narrows the
 * contacts by number or by name. The call itself uses the phone's own call screen.
 */
@Composable
fun PhoneScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pending?.let { placeCall(ctx, it, direct = ok) }
        pending = null
    }
    fun call(n: String) {
        if (n.isBlank()) return
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (granted) placeCall(ctx, n, direct = true)
        else { pending = n; callPermission.launch(Manifest.permission.CALL_PHONE) }
    }

    // Searching by name hides the keypad so the keyboard and the results have the room.
    var searching by rememberSaveable { mutableStateOf(false) }
    var nameQuery by rememberSaveable { mutableStateOf("") }
    val stopSearching = { searching = false; nameQuery = "" }
    BackHandler(enabled = searching) { stopSearching() }

    PageColumn {
        PageHeader(if (searching) "contacts" else "phone", onBack = if (searching) stopSearching else onBack)
        Contacts(
            typed = number,
            nameQuery = nameQuery,
            searching = searching,
            onStartSearch = { searching = true },
            onNameQuery = { nameQuery = it },
            modifier = Modifier.weight(1f),
            onCall = { call(it) }
        )
        if (searching) return@PageColumn
        SolidRule()
        Keypad(number, onNumber = { number = it })
        SolidRule()
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Action("call", Modifier.weight(1f)) { call(number) }
            Action("text", Modifier.weight(1f)) {
                if (number.isNotBlank()) openIntent(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
            }
            Action("save", Modifier.weight(1f)) {
                openIntent(
                    ctx,
                    Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                )
            }
        }
    }
}

@Composable
private fun Action(label: String, modifier: Modifier, onPress: () -> Unit) {
    Box(modifier.height(46.dp).press(onPress = onPress), contentAlignment = Alignment.Center) {
        BasicText(label, style = textStyle(18f, bold = true, fixed = true), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Keypad(number: String, onNumber: (String) -> Unit) {
    val ink = LocalPalette.current.ink
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // The number, with backspace (hold to clear)
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                number.ifEmpty { " " },
                Modifier.weight(1f).padding(start = 4.dp),
                style = textStyle(24f, bold = true, fixed = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (number.isNotEmpty()) {
                Box(
                    Modifier.press(onLongPress = { onNumber("") }) { onNumber(number.dropLast(1)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) { BasicText("⌫", style = textStyle(22f, bold = true, fixed = true)) }
            }
        }
        KEYS.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { (digit, letters) ->
                    Row(
                        Modifier.weight(1f).height(42.dp).border(2.dp, ink)
                            .press(onLongPress = if (digit == "0") ({ onNumber("$number+") }) else null) { onNumber(number + digit) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(digit, style = textStyle(20f, bold = true, fixed = true), maxLines = 1, softWrap = false)
                        if (letters.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            BasicText(letters, style = textStyle(12f, fixed = true), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Contacts(
    typed: String,
    nameQuery: String,
    searching: Boolean,
    onStartSearch: () -> Unit,
    onNameQuery: (String) -> Unit,
    modifier: Modifier,
    onCall: (String) -> Unit
) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val gate = rememberPermissionGate(listOf(Manifest.permission.READ_CONTACTS))
    if (!gate.granted) {
        Column(modifier) { AskPermission("To show your contacts here, the phone needs permission to read them.", gate) }
        return
    }
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    val tick = LocalResumeTick.current
    LaunchedEffect(tick) { contacts = withContext(Dispatchers.IO) { loadContacts(ctx) } }
    val all = contacts

    val digits = typed.filter { it.isDigit() }
    val name = nameQuery.trim()
    val shown = when {
        all == null -> emptyList()
        searching && name.isNotEmpty() -> all.filter { c -> c.name.contains(name, ignoreCase = true) }
        !searching && digits.isNotEmpty() -> all.filter {
            digits in it.digits || it.t9.split(' ').any { w -> w.startsWith(digits) } || digits in it.t9.replace(" ", "")
        }
        else -> all
    }

    Column(modifier) {
        // find: tap to type a name
        val focus = remember { FocusRequester() }
        Row(
            Modifier.fillMaxWidth().height(40.dp).press(onPress = onStartSearch).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText("find: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            if (searching) {
                BasicTextField(
                    value = nameQuery,
                    onValueChange = onNameQuery,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    textStyle = textStyle(18f),
                    cursorBrush = SolidColor(palette.ink)
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                BasicText(
                    if (digits.isEmpty()) "tap to type a name" else "matching the keypad",
                    style = textStyle(13f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SolidRule()

        if (all == null) {
            Hint("Loading contacts.")
            return@Column
        }
        val rows: List<Contact?> = (if (searching) emptyList() else listOf<Contact?>(null)) + shown
        PagedList(rows, Modifier.weight(1f), resetKey = digits + "|" + name) { c ->
            if (c == null) {
                ListRow("+ new contact", trailing = "") {
                    openIntent(
                        ctx,
                        Intent(ContactsContract.Intents.Insert.ACTION)
                            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, typed)
                    )
                }
            } else {
                ListRow(c.name.lowercase(), trailing = "", onLongPress = { placeCall(ctx, c.number, direct = false) }) { onCall(c.number) }
            }
        }
        FooterLine(
            when {
                searching && name.isNotEmpty() -> "${shown.size} of ${all.size} contacts"
                !searching && digits.isNotEmpty() -> "${shown.size} matching"
                else -> "${all.size} contacts"
            }
        )
    }
}

private fun loadContacts(ctx: Context): List<Contact> {
    val out = mutableListOf<Contact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    try {
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                out += Contact(name, num, toT9(name), num.filter { it.isDigit() })
            }
        }
    } catch (e: SecurityException) {
        return emptyList()
    }
    return out.distinctBy { it.name + it.digits }
}

private fun openIntent(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

/** [direct] = call straight away; otherwise open the number in the phone's own dialer. */
private fun placeCall(ctx: Context, number: String, direct: Boolean) {
    openIntent(ctx, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
}
