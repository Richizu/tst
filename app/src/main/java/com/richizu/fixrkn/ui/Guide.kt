package com.richizu.fixrkn.ui

import android.content.Context
import com.richizu.fixrkn.bypass.StrategyStore
import com.richizu.fixrkn.core.Network
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "Check it yourself" flow: opens YouTube with the current strategy and, when the user
 * comes back, asks whether videos play. A "no" moves on to the next best strategy.
 */
object Guide {
    enum class Ask { None, DidItWork, Exhausted }

    val ask = MutableStateFlow(Ask.None)
    private var openedAt = 0L

    fun openAndCheck(context: Context) {
        if (!Actions.openYoutube(context)) return
        openedAt = System.currentTimeMillis()
    }

    /** Called from the activity's onResume. */
    fun onReturn() {
        if (openedAt != 0L && System.currentTimeMillis() - openedAt > 4000) {
            openedAt = 0L
            ask.value = Ask.DidItWork
        } else {
            openedAt = 0L
        }
    }

    fun answer(context: Context, worked: Boolean) {
        val current = Settings.activeStrategy
        if (current != null) StrategyStore.setFeedback(current, worked)
        if (worked) {
            Settings.youtubeVerified = true
            ask.value = Ask.None
            return
        }
        val next = StrategyStore.ranked().firstOrNull { it != current && StrategyStore.entries.value.firstOrNull { e -> e.command == it }?.feedback != false }
        if (next == null) {
            ask.value = Ask.Exhausted
            return
        }
        Control.applyStrategy(context, next, Network.currentKind(context))
        ask.value = Ask.None
        openAndCheck(context)
    }

    fun dismiss() {
        ask.value = Ask.None
    }
}
