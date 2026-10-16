package com.richizu.fixrkn

import android.app.Application
import com.richizu.fixrkn.bypass.StrategyStore
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    companion object {
        /** Lives as long as the process; used for work that must outlive a screen (scans, downloads). */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        StrategyStore.init(this)
        Notifications.createChannels(this)
        AppLog.i("App", "RichizuFixRKN ${BuildConfig.VERSION_NAME} on Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
    }
}
