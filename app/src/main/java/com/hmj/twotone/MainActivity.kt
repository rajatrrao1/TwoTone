package com.hmj.twotone

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.hmj.twotone.calendar.CalEvent
import com.hmj.twotone.calendar.loadEvents
import com.hmj.twotone.clock.AlarmStore
import com.hmj.twotone.coffee.Bean
import com.hmj.twotone.ui.AlarmEditScreen
import com.hmj.twotone.ui.ChargingScreen
import com.hmj.twotone.ui.ClockScreen
import com.hmj.twotone.ui.BooksScreen
import com.hmj.twotone.ui.ReaderScreen
import com.hmj.twotone.ui.MusicFolderScreen
import com.hmj.twotone.ui.MusicSettingsScreen
import com.hmj.twotone.ui.StopwatchScreen
import com.hmj.twotone.ui.TimerScreen
import com.hmj.twotone.coffee.CoffeeStore
import com.hmj.twotone.notes.NotesStore
import com.hmj.twotone.ui.BeanEditScreen
import com.hmj.twotone.ui.BeansScreen
import com.hmj.twotone.ui.BrewDetailScreen
import com.hmj.twotone.ui.BrewScreen
import com.hmj.twotone.ui.CoffeeScreen
import com.hmj.twotone.ui.FontSlot
import com.hmj.twotone.ui.ImportBeanScreen
import com.hmj.twotone.ui.LocalPixelGrids
import com.hmj.twotone.ui.PixelGrids
import com.hmj.twotone.ui.TEXT_SIZES
import com.hmj.twotone.ui.LocalTextSizeStep
import com.hmj.twotone.ui.LocalOwnStatusBar
import androidx.compose.foundation.layout.statusBarsPadding
import com.hmj.twotone.ui.pixelGrid
import com.hmj.twotone.ui.LocalSmallFontFamily
import com.hmj.twotone.ui.AppsScreen
import com.hmj.twotone.ui.CalendarScreen
import com.hmj.twotone.ui.ClockStyle
import com.hmj.twotone.ui.FontsScreen
import com.hmj.twotone.ui.HomeListScreen
import com.hmj.twotone.ui.HomeRow
import com.hmj.twotone.ui.HomeScreen
import com.hmj.twotone.ui.InkOnPaper
import com.hmj.twotone.ui.LocalFontFamily
import com.hmj.twotone.ui.LocalPalette
import com.hmj.twotone.ui.LocalResumeTick
import com.hmj.twotone.ui.LocalTextScale
import com.hmj.twotone.ui.MusicScreen
import com.hmj.twotone.ui.NoteEditScreen
import com.hmj.twotone.ui.NotesScreen
import com.hmj.twotone.ui.Palette
import com.hmj.twotone.ui.PaperOnInk
import com.hmj.twotone.ui.PhoneScreen
import com.hmj.twotone.ui.PickAppScreen
import com.hmj.twotone.ui.SectionDetailScreen
import com.hmj.twotone.ui.SectionsScreen
import com.hmj.twotone.ui.SettingsScreen
import com.hmj.twotone.ui.WeatherPlaceScreen
import com.hmj.twotone.ui.WeatherScreen
import com.hmj.twotone.ui.resolveFont
import com.hmj.twotone.weather.Forecast
import com.hmj.twotone.weather.Place
import com.hmj.twotone.weather.WeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Every page the launcher can show. The back button walks back through them. */
sealed interface Page {
    data object Home : Page
    data object Apps : Page
    data object Settings : Page
    data object HomeList : Page
    data object Sections : Page
    data object WeatherPlace : Page
    data object Music : Page
    data object Weather : Page
    data object Notes : Page
    data object Phone : Page
    data object Calendar : Page
    data class SectionDetail(val section: Section) : Page
    data class PickApp(val section: Section) : Page
    data object Clock : Page
    data object MusicSettings : Page
    data object MusicFolder : Page
    data object Books : Page
    data object News : Page
    data object NewsSources : Page
    data object NewsLink : Page
    data class Article(val link: String, val storyId: String? = null, val savedId: String? = null) : Page
    data object StatusPanel : Page
    data object AddToHome : Page
    data object Notifications : Page
    data object StatusBarSettings : Page
    data object Eink : Page
    data class Reader(val bookId: String) : Page
    data object Timer : Page
    data object Stopwatch : Page
    data class AlarmEdit(val id: Int) : Page
    data class NoteEdit(val path: String) : Page
    data object NoteRecord : Page
    data object NotesPlace : Page
    data class Shared(val text: String, val subject: String) : Page
    data class Fonts(val slot: FontSlot) : Page
    data object Coffee : Page
    data object Beans : Page
    data object ImportBean : Page
    data object NewBrew : Page
    data class BeanEdit(val id: Long) : Page
    data class BrewDetail(val id: Long) : Page
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var notes: NotesStore
    private lateinit var coffee: CoffeeStore
    /** Beans read from a shop page, shown for checking before they are saved. */
    private var beanDraft: Bean? = null

    private val stack = mutableStateListOf<Page>(Page.Home)
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val resumeTick = mutableIntStateOf(0)
    private val forecast = mutableStateOf<Forecast?>(null)
    private val weatherLoading = mutableStateOf(false)
    private val weatherError = mutableStateOf<String?>(null)
    /** Next events for the home screen; null when calendar permission isn't given. */
    private val homeEvents = mutableStateOf<List<CalEvent>?>(null)

    /** 0 = normal, 1 = full black frame, 2 = full white frame (clears E-ink ghosting). */
    private val refreshPhase = mutableIntStateOf(0)
    private val phoneStatus = mutableStateOf(com.hmj.twotone.ui.PhoneStatus())

    private fun updateStatus() {
        phoneStatus.value = com.hmj.twotone.ui.readStatus(this, batteryLevel.intValue, charging.value)
    }

    /** Hides or shows Android's status bar to match the setting. */
    private fun applyStatusBar() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (prefs.ownStatusBar) {
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        } else {
            c.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }

    /** Opens KompaktOS's own settings app (where the e-ink modes are), if it can be found. */
    private fun openKompaktSettings() {
        val app = apps.value.firstOrNull { it.label.equals("Kompakt", ignoreCase = true) }
            ?: apps.value.firstOrNull { it.pkg.contains("mudita", ignoreCase = true) && it.label.contains("kompakt", ignoreCase = true) }
            ?: apps.value.firstOrNull { it.pkg.contains("mudita", ignoreCase = true) && "setting" in it.pkg.lowercase() }
        if (app != null) launchApp(app)
        else {
            Toast.makeText(this, "The Kompakt settings app wasn't found; opening Android settings.", Toast.LENGTH_LONG).show()
            openIntent(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    // ---- charging page
    private val charging = mutableStateOf(false)
    private val batteryLevel = mutableIntStateOf(-1)
    /** Tapped away; comes back next time the phone is plugged in. */
    private val chargingDismissed = mutableStateOf(false)
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
            val now = plugged || status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            if (!now) chargingDismissed.value = false
            charging.value = now
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) batteryLevel.intValue = level * 100 / scale
            updateStatus()
        }
    }

    private fun chargingPageWanted(): Boolean {
        if (!charging.value || chargingDismissed.value || stack.last() != Page.Home) return false
        return when (prefs.chargingClock) {
            2 -> true
            1 -> java.time.LocalTime.now().hour.let { it >= 21 || it < 7 }
            else -> false
        }
    }
    private var leftForAnotherApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // Draw edge to edge, and hide Android's status bar before the first frame,
        // so the page never changes size afterwards.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyStatusBar()
        notes = NotesStore(this)
        coffee = CoffeeStore(this)
        AlarmStore.scheduleAll(this)
        // Bring back the last music queue (paused), if the phone allows reading music.
        if (ContextCompat.checkSelfPermission(this, com.hmj.twotone.ui.audioPermission()) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch { com.hmj.twotone.music.Music.load(this@MainActivity) }
        }
        handleIntent(intent)
        loadCachedWeather()

        setContent {
            val palette = if (prefs.inverted) PaperOnInk else InkOnPaper
            val scale = TEXT_SIZES[prefs.textSize.coerceIn(0, TEXT_SIZES.lastIndex)].second
            val font = remember(prefs.font, prefs.fontStamp) { resolveFont(this@MainActivity, prefs.font, FontSlot.LARGE) }
            // Next to UNSCII 16, small text uses UNSCII 8 (when its file is there), drawn at 3 × 8 = 24 px.
            val smallChoice = if (prefs.font == "unscii16" && com.hmj.twotone.ui.isAssetFontPresent(this@MainActivity, "unscii8")) "unscii8" else prefs.font
            val smallFont = remember(smallChoice, prefs.fontStamp) { resolveFont(this@MainActivity, smallChoice, FontSlot.LARGE) }
            LaunchedEffect(palette) { colourSystemBars(palette) }

            CompositionLocalProvider(
                LocalPalette provides palette,
                LocalTextScale provides scale,
                LocalTextSizeStep provides prefs.textSize,
                LocalFontFamily provides font,
                LocalSmallFontFamily provides smallFont,
                LocalOwnStatusBar provides prefs.ownStatusBar,
                LocalPixelGrids provides PixelGrids(
                    large = pixelGrid(prefs.font, FontSlot.LARGE, prefs.pixelSnap),
                    small = pixelGrid(smallChoice, FontSlot.LARGE, prefs.pixelSnap)
                ),
                LocalResumeTick provides resumeTick.intValue
            ) {
                Box(Modifier.fillMaxSize().background(palette.paper)) {
                    BackHandler { pop() }
                    // Re-checked every minute so "at night" starts and ends on time.
                    var minuteTick by androidx.compose.runtime.remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) { while (true) { delay(60_000L); minuteTick++ } }
                    val showCharging = minuteTick >= 0 && chargingPageWanted()
                    LaunchedEffect(showCharging) {
                        if (showCharging) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    LaunchedEffect(prefs.ownStatusBar) { applyStatusBar() }
                    LaunchedEffect(Unit) { while (true) { updateStatus(); delay(30_000L) } }
                    // Clean refreshes asked for by page turns
                    com.hmj.twotone.Ghosting.everyTurns = when (prefs.refreshMode) { 2 -> 10; 3 -> 5; else -> 0 }
                    val ghostRequests = com.hmj.twotone.Ghosting.requests.intValue
                    LaunchedEffect(ghostRequests) { if (ghostRequests > 0) refreshPhase.intValue = 1 }

                    if (showCharging) ChargingScreen(batteryLevel.intValue) { chargingDismissed.value = true }
                    else androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        if (prefs.ownStatusBar) {
                            androidx.compose.foundation.layout.Box {
                                com.hmj.twotone.ui.LauncherStatusBar(
                                    phoneStatus.value, prefs.statusItems,
                                    // The home page shows the time large already.
                                    showTime = stack.last() != Page.Home,
                                    notificationCount = com.hmj.twotone.notify.Notifications.list.size,
                                    onBell = { if (stack.last() != Page.Notifications) push(Page.Notifications) },
                                    onNote = { com.hmj.twotone.notify.Notifications.open(it) }
                                ) {
                                    if (stack.last() != Page.StatusPanel) push(Page.StatusPanel)
                                }
                            }
                        }
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { PageContent(stack.last()) }
                    }

                    // Clean refresh: one black frame, one white frame, then the page again.
                    val phase = refreshPhase.intValue
                    if (phase != 0) {
                        Box(Modifier.fillMaxSize().background(if (phase == 1) palette.ink else palette.paper))
                    }
                    LaunchedEffect(phase) {
                        if (phase == 1) { delay(160); refreshPhase.intValue = 2 }
                        else if (phase == 2) { delay(160); refreshPhase.intValue = 0 }
                    }
                }
            }
        }
    }

    @Composable
    private fun PageContent(page: Page) {
        when (page) {
            Page.Home -> HomeScreen(
                rows = homeRows(),
                clockStyle = ClockStyle.of(prefs.clock),
                showWeather = prefs.showWeather,
                showCalendar = prefs.showCalendar,
                forecast = forecast.value,
                hasPlace = prefs.place != null,
                events = homeEvents.value,
                onClockTap = { openSection(Section.CLOCK) },
                onClockLongPress = { refreshPhase.intValue = 1 },
                onWeather = { openSection(Section.WEATHER) },
                onCalendar = { openSection(Section.CALENDAR) },
                onMusic = { openSection(Section.MUSIC) }
            )

            Page.Apps -> AppsScreen(
                apps = apps.value,
                pinned = prefs.pinned,
                onLaunch = ::launchApp,
                onTogglePin = { prefs.togglePinned(it.key) },
                onAppInfo = {
                    openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.pkg, null)))
                },
                onUninstall = { openIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:${it.pkg}"))) },
                onBack = ::pop
            )

            Page.Settings -> SettingsScreen(
                prefs = prefs,
                onEditHomeList = { push(Page.HomeList) },
                onSections = { push(Page.Sections) },
                onMusicSettings = { push(Page.MusicSettings) },
                onStatusBar = { push(Page.StatusBarSettings) },
                onEink = { push(Page.Eink) },
                onFonts = { push(Page.Fonts(it)) },
                onWeatherPlace = { push(Page.WeatherPlace) },
                onChooseHomeApp = ::chooseHomeApp,
                onAndroidSettings = { openIntent(Intent(Settings.ACTION_SETTINGS)) },
                onBack = ::pop
            )

            Page.HomeList -> HomeListScreen(
                pinned = prefs.pinned,
                labelOf = ::labelOf,
                onChange = prefs::setPinned,
                onAdd = { push(Page.AddToHome) },
                onBack = ::pop
            )

            Page.Sections -> SectionsScreen(
                handlerName = ::handlerName,
                onSection = { push(Page.SectionDetail(it)) },
                onBack = ::pop
            )

            is Page.SectionDetail -> SectionDetailScreen(
                section = page.section,
                handlerName = handlerName(page.section),
                onHome = page.section.key in prefs.pinned,
                onPickApp = { push(Page.PickApp(page.section)) },
                onToggleHome = { prefs.togglePinned(page.section.key) },
                onOpen = { openSection(page.section) },
                onBack = ::pop
            )

            is Page.PickApp -> PickAppScreen(
                title = "${page.section.label} opens",
                noAppLabel = "built-in ${page.section.label}",
                apps = apps.value,
                currentKey = prefs.sectionApp(page.section),
                onPick = { prefs.setSectionApp(page.section, it?.key); pop() },
                onBack = ::pop
            )

            Page.Clock -> ClockScreen(
                onEditAlarm = { push(Page.AlarmEdit(it)) },
                onTimer = { push(Page.Timer) },
                onStopwatch = { push(Page.Stopwatch) },
                onBack = ::pop
            )

            is Page.AlarmEdit -> AlarmEditScreen(page.id, onDone = ::pop, onBack = ::pop)

            Page.Timer -> TimerScreen(onBack = ::pop)

            Page.Stopwatch -> StopwatchScreen(onBack = ::pop)

            is Page.Fonts -> FontsScreen(prefs, onBack = ::pop)

            Page.Coffee -> CoffeeScreen(
                store = coffee,
                onNewBrew = { push(Page.NewBrew) },
                onBeans = { push(Page.Beans) },
                onBrew = { push(Page.BrewDetail(it)) },
                onBack = ::pop
            )

            Page.Beans -> BeansScreen(
                store = coffee,
                onImport = { push(Page.ImportBean) },
                onAdd = { beanDraft = null; push(Page.BeanEdit(System.currentTimeMillis())) },
                onBean = { beanDraft = null; push(Page.BeanEdit(it)) },
                onBack = ::pop
            )

            Page.ImportBean -> ImportBeanScreen(
                onImported = { bean ->
                    beanDraft = bean
                    pop()
                    push(Page.BeanEdit(bean.id))
                },
                onBack = ::pop
            )

            is Page.BeanEdit -> BeanEditScreen(
                store = coffee,
                beanId = page.id,
                draft = beanDraft?.takeIf { it.id == page.id },
                onDone = { beanDraft = null; pop() },
                onBack = ::pop
            )

            Page.NewBrew -> BrewScreen(coffee, onSaved = ::pop, onBack = ::pop)

            is Page.BrewDetail -> BrewDetailScreen(
                store = coffee,
                brewId = page.id,
                onBrewAgain = { pop(); push(Page.NewBrew) },
                onDeleted = ::pop,
                onBack = ::pop
            )

            Page.WeatherPlace -> WeatherPlaceScreen(
                onPicked = { setPlace(it) },
                onBack = ::pop
            )

            Page.Weather -> WeatherScreen(
                forecast = forecast.value,
                hasPlace = prefs.place != null,
                loading = weatherLoading.value,
                error = weatherError.value,
                onRefresh = { refreshWeather(force = true) },
                onChangePlace = { push(Page.WeatherPlace) },
                onBack = ::pop
            )

            Page.Music -> MusicScreen(onBack = ::pop, onSettings = { push(Page.MusicSettings) })

            Page.MusicSettings -> MusicSettingsScreen(onFolder = { push(Page.MusicFolder) }, onBack = ::pop)

            Page.MusicFolder -> MusicFolderScreen(onBack = ::pop)

            Page.StatusPanel -> com.hmj.twotone.ui.StatusPanelScreen(
                status = phoneStatus.value,
                onClock = { push(Page.Clock) },
                onNotifications = { push(Page.Notifications) },
                onKompakt = ::openKompaktSettings,
                onBack = ::pop
            )

            Page.Notifications -> com.hmj.twotone.ui.NotificationsScreen(onBack = ::pop)

            Page.AddToHome -> com.hmj.twotone.ui.AddToHomeScreen(
                sections = Section.entries.filter { it.key !in prefs.pinned }.map { it.key to it.label },
                apps = apps.value.filter { it.key !in prefs.pinned }.map { it.key to it.label.lowercase() },
                onAdd = { key -> prefs.setPinned(prefs.pinned + key); pop() },
                onBack = ::pop
            )

            Page.StatusBarSettings -> com.hmj.twotone.ui.StatusBarSettingsScreen(prefs, onBack = ::pop)

            Page.Eink -> com.hmj.twotone.ui.EinkScreen(prefs, onKompakt = ::openKompaktSettings, onBack = ::pop)

            Page.News -> com.hmj.twotone.ui.NewsScreen(
                onStory = { push(Page.Article(it.link, storyId = it.id)) },
                onSaved = { id -> com.hmj.twotone.news.News.savedArticle(id)?.let { push(Page.Article(it.link, savedId = id)) } },
                onSources = { push(Page.NewsSources) },
                onReadLink = { push(Page.NewsLink) },
                onBack = ::pop
            )

            Page.NewsSources -> com.hmj.twotone.ui.NewsSourcesScreen(onBack = ::pop)

            Page.NewsLink -> com.hmj.twotone.ui.NewsLinkScreen(onRead = { pop(); push(Page.Article(it)) }, onBack = ::pop)

            is Page.Article -> com.hmj.twotone.ui.ArticleScreen(page.link, page.storyId, page.savedId, onBack = ::pop)

            Page.Books -> BooksScreen(onOpen = { push(Page.Reader(it)) }, onBack = ::pop)

            is Page.Reader -> ReaderScreen(page.bookId, onBack = ::pop)

            Page.Notes -> NotesScreen(
                notes,
                onOpen = { push(Page.NoteEdit(it)) },
                onRecord = { push(Page.NoteRecord) },
                onPlace = { push(Page.NotesPlace) },
                onBack = ::pop
            )

            Page.NoteRecord -> com.hmj.twotone.ui.RecordScreen(notes, onSaved = { pop(); push(Page.NoteEdit(it)) }, onBack = ::pop)

            Page.NotesPlace -> com.hmj.twotone.ui.NotesPlaceScreen(notes, onBack = ::pop)

            is Page.Shared -> com.hmj.twotone.ui.SharedScreen(
                text = page.text,
                subject = page.subject,
                onRead = { url -> pop(); push(Page.Article(url)) },
                onNote = { title, body ->
                    lifecycleScope.launch {
                        val n = withContext(Dispatchers.IO) { notes.reload(); notes.create(title, body) }
                        pop(); push(Page.Notes); push(Page.NoteEdit(n.path))
                    }
                },
                onBack = ::pop
            )

            is Page.NoteEdit -> NoteEditScreen(notes, page.path, onBack = ::pop)

            Page.Phone -> PhoneScreen(onBack = ::pop)

            Page.Calendar -> CalendarScreen(onBack = ::pop)
        }
    }

    // ---- navigation ---------------------------------------------------------

    private fun push(p: Page) { stack.add(p) }

    private fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    private fun goHome() {
        stack.clear()
        stack.add(Page.Home)
    }

    /** Opens the app chosen for [s], or the built-in version when none is chosen (or it was uninstalled). */
    private fun openSection(s: Section) {
        val app = prefs.sectionApp(s)?.let { key -> apps.value.firstOrNull { it.key == key } }
        if (app != null) {
            launchApp(app)
            return
        }
        push(
            when (s) {
                Section.PHONE -> Page.Phone
                Section.MUSIC -> Page.Music
                Section.NOTES -> Page.Notes
                Section.CALENDAR -> Page.Calendar
                Section.WEATHER -> if (prefs.place == null) Page.WeatherPlace else Page.Weather
                Section.COFFEE -> Page.Coffee
                Section.CLOCK -> Page.Clock
                Section.BOOKS -> Page.Books
                Section.NEWS -> Page.News
            }
        )
    }

    private fun homeRows(): List<HomeRow> {
        val byKey = apps.value.associateBy { it.key }
        val rows = prefs.pinned.mapNotNull { k ->
            val s = Section.fromKey(k)
            when {
                s != null -> HomeRow(k, s.label) { openSection(s) }
                byKey[k] != null -> byKey.getValue(k).let { a -> HomeRow(k, a.label.lowercase()) { launchApp(a) } }
                else -> null
            }
        }
        return rows + HomeRow("_apps", "apps") { push(Page.Apps) } + HomeRow("_settings", "settings") { push(Page.Settings) }
    }

    private fun labelOf(key: String): String =
        Section.fromKey(key)?.label
            ?: apps.value.firstOrNull { it.key == key }?.label?.lowercase()
            ?: "(app not installed)"

    private fun handlerName(s: Section): String {
        val key = prefs.sectionApp(s) ?: return "built-in"
        return apps.value.firstOrNull { it.key == key }?.label?.lowercase() ?: "built-in"
    }

    // ---- lifecycle ----------------------------------------------------------

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStop() {
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { }
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        if (leftForAnotherApp && prefs.refreshMode >= 1) refreshPhase.intValue = 1
        applyStatusBar()
        updateStatus()
        leftForAnotherApp = false
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { loadApps(this@MainActivity) }
            apps.value = list
            setUpHomeList(list)
        }
        refreshWeather(force = false)
        refreshHomeEvents()
    }

    /** Pressing the home button always lands on the home page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Home button → home; the music or alarm notification → that page. */
    private fun handleIntent(intent: Intent?) {
        when {
            intent == null -> Unit
            intent.action == ACTION_OPEN_MUSIC -> { goHome(); push(Page.Music) }
            intent.action == ACTION_OPEN_CLOCK -> { goHome(); push(Page.Clock) }
            // "Share → Two Tone" from a browser: read the page as text
            // Shared from another app: read a link as text, or keep anything as a note
            intent.action == Intent.ACTION_SEND -> {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                if (shared.isNotBlank()) { goHome(); push(Page.Shared(shared, subject)) }
            }
            intent.hasCategory(Intent.CATEGORY_HOME) -> goHome()
        }
    }

    companion object {
        const val ACTION_OPEN_MUSIC = "com.hmj.twotone.OPEN_MUSIC"
        const val ACTION_OPEN_CLOCK = "com.hmj.twotone.OPEN_CLOCK"
    }

    /** Volume keys turn pages on list screens, when that setting is on. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Not on home (volume should stay volume there) and not in music (the wheel has the list).
        val onList = PageKeys.listening > 0 && stack.last() != Page.Music && stack.last() != Page.Home
        // In a book the volume keys always turn pages.
        if ((prefs.volumePaging && onList) || (stack.last() is Page.Reader && PageKeys.listening > 0)) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { PageKeys.turns.tryEmit(1); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { PageKeys.turns.tryEmit(-1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---- apps ---------------------------------------------------------------

    private fun launchApp(app: AppEntry) {
        openIntent(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.pkg, app.cls))
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            leftForAnotherApp = true
        } catch (e: Exception) {
            Toast.makeText(this, "That can't be opened on this phone.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Android's own picker, so any launcher can be the home screen again. */
    private fun chooseHomeApp() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            openIntent(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * First run: phone, messages, coffee, music, camera, notes, calendar, weather.
     * After an update, any section that is new since last time is added to the top once;
     * sections you removed before stay removed.
     */
    private fun setUpHomeList(list: List<AppEntry>) {
        if (list.isEmpty()) return
        val allIds = Section.entries.map { it.id }.toSet()
        if (!prefs.hasSavedHomeList) {
            fun appFor(intent: Intent): String? {
                val pkg = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
                return list.firstOrNull { it.pkg == pkg }?.key
            }
            val messages = appFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            val camera = appFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            prefs.setPinned(
                listOfNotNull(
                    Section.PHONE.key, messages, Section.COFFEE.key, Section.MUSIC.key, camera,
                    Section.NOTES.key, Section.CALENDAR.key, Section.WEATHER.key
                ).distinct()
            )
        } else {
            val seen = prefs.seenSections()
            val fresh = Section.entries.filter { it != Section.CLOCK && it.id !in seen && it.key !in prefs.pinned }.map { it.key }
            if (fresh.isNotEmpty()) prefs.setPinned(fresh + prefs.pinned)
        }
        prefs.markSectionsSeen(allIds)
    }

    // ---- weather and calendar ----------------------------------------------

    private fun loadCachedWeather() {
        val raw = prefs.weatherRaw ?: return
        val place = prefs.place ?: return
        forecast.value = try { WeatherApi.parse(raw, place.name, prefs.weatherFetchedAt) } catch (e: Exception) { null }
    }

    private fun setPlace(p: Place) {
        prefs.setPlace(p)
        forecast.value = null
        pop()
        if (stack.last() != Page.Weather && stack.last() != Page.Settings) push(Page.Weather)
        refreshWeather(force = true)
    }

    /** Fetches at most every 30 minutes unless [force] is set. */
    private fun refreshWeather(force: Boolean) {
        val place = prefs.place ?: return
        if (!force && System.currentTimeMillis() - prefs.weatherFetchedAt < 30 * 60 * 1000) {
            loadCachedWeather()
            // A cache from an older version may not read; then fetch fresh.
            if (forecast.value != null) return
        }
        if (weatherLoading.value) return
        weatherLoading.value = true
        weatherError.value = null
        lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()
                val raw = withContext(Dispatchers.IO) { WeatherApi.fetchForecast(place) }
                val parsed = withContext(Dispatchers.Default) { WeatherApi.parse(raw, place.name, now) }
                prefs.saveWeather(raw, now)
                forecast.value = parsed
            } catch (e: Exception) {
                weatherError.value = WeatherApi.explain(e)
            } finally {
                weatherLoading.value = false
            }
        }
    }

    private fun refreshHomeEvents() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) { homeEvents.value = null; return }
        lifecycleScope.launch {
            val now = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfTomorrow = LocalDate.now().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            homeEvents.value = withContext(Dispatchers.IO) { loadEvents(this@MainActivity, now, endOfTomorrow) }
        }
    }

    @Suppress("DEPRECATION")
    private fun colourSystemBars(p: Palette) {
        window.statusBarColor = p.paper.toArgb()
        window.navigationBarColor = p.paper.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = p == InkOnPaper
            isAppearanceLightNavigationBars = p == InkOnPaper
        }
    }
}
