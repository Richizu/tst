package com.richizu.fixrkn.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.richizu.fixrkn.MainActivity
import com.richizu.fixrkn.core.Settings

/** Quick Settings tile toggling protection. */
class QuickTile : TileService() {
    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, QuickTile::class.java)) }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        if (Control.anyRunning || Control.vpn.value == RunState.Paused) {
            Control.stopAll(this)
        } else if (!Settings.onboarded || (Settings.youtubeEnabled && Control.vpnConsentIntent(this) != null)) {
            // Needs the app UI (onboarding or the VPN consent dialog).
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction(MainActivity.ACTION_START_FROM_TILE)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        } else {
            Control.startAll(this)
        }
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Control.anyRunning
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                Control.vpn.value == RunState.Paused -> "Пауза"
                on -> "Работает"
                else -> "Выключено"
            }
        }
        tile.updateTile()
    }
}
