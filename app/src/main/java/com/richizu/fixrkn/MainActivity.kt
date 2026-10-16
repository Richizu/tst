package com.richizu.fixrkn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.richizu.fixrkn.ui.Guide
import com.richizu.fixrkn.ui.Shell

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_START_FROM_TILE = "com.richizu.fixrkn.START_FROM_TILE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val fromTile = intent?.action == ACTION_START_FROM_TILE
        setContent { Shell(startRequested = fromTile) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_START_FROM_TILE) {
            setContent { Shell(startRequested = true) }
        }
    }

    override fun onResume() {
        super.onResume()
        Guide.onReturn()
    }
}
