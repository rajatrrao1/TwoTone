package com.hmj.twotone.music

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import com.hmj.twotone.MainActivity
import com.hmj.twotone.Prefs
import com.hmj.twotone.Visible
import com.hmj.twotone.ui.LauncherTheme
import com.hmj.twotone.ui.ListRow
import com.hmj.twotone.ui.LocalPalette
import com.hmj.twotone.ui.MusicScreen
import com.hmj.twotone.ui.SolidRule

/**
 * The music player on top of the lock screen. Everything in the player works;
 * leaving it (back, or "◂ back") goes to the lock screen, which asks to unlock.
 * "unlock" asks to unlock and then opens music in the launcher.
 */
class LockPlayerActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        // The notification that brought us here has done its job.
        getSystemService(android.app.NotificationManager::class.java)?.cancel(MusicService.LOCK_NOTIFICATION_ID)
        val prefs = Prefs(this)
        setContent {
            LauncherTheme(this, prefs, resumeTick.intValue) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            MusicScreen(onBack = { finish() }, onSettings = { unlockThenOpenMusic() })
                        }
                        SolidRule()
                        ListRow("unlock", trailing = "▸") { unlockThenOpenMusic() }
                    }
                }
            }
        }
    }

    /** Asks for the PIN / fingerprint; once unlocked, opens music in the launcher. */
    private fun unlockThenOpenMusic() {
        val km = getSystemService(KeyguardManager::class.java)
        val open = {
            startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        if (Build.VERSION.SDK_INT >= 26 && km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { open() }
            })
        } else open()
    }

    override fun onStart() { super.onStart(); Visible.on = true; resumeTick.intValue++ }
    override fun onStop() { Visible.on = false; super.onStop() }

    /** Nothing is playing any more: there's no reason to stay over the lock screen. */
    override fun onResume() {
        super.onResume()
        if (Music.current == null) finish()
    }
}
