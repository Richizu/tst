package com.richizu.fixrkn.bypass

import android.content.Context
import com.richizu.fixrkn.App
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Network
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScanState(
    val running: Boolean = false,
    /** false: quick pass over every strategy, true: thorough check of the finalists. */
    val finalPhase: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String? = null,
    val found: Int = 0,
    val startedAt: Long = 0,
    val best: String? = null,
    val finishedAt: Long = 0,
    /** A single-strategy probe is in progress for this command. */
    val probing: String? = null
)

/** Finds working strategies for the current network. Runs in the application scope. */
object Scanner {
    private const val TAG = "Scanner"
    private const val FINALISTS = 5

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state
    private var job: Job? = null
    private val prober = Prober()
    @Volatile private var finishEarly = false

    val busy get() = _state.value.running || _state.value.probing != null

    fun start(context: Context, only: List<String>? = null) {
        if (busy) return
        val app = context.applicationContext
        finishEarly = false
        val strategies = only ?: StrategyStore.all()
        _state.value = ScanState(running = true, total = strategies.size, startedAt = System.currentTimeMillis())
        job = App.scope.launch {
            val resumeAfter = Control.stopBypassForTesting(app)
            AppLog.i(TAG, "Scan of ${strategies.size} strategies started")
            val quick = IntArray(strategies.size)
            for ((i, s) in strategies.withIndex()) {
                if (!isActive || finishEarly) break
                _state.value = _state.value.copy(current = s, done = i)
                val passed = prober.withStrategy(s, 0) { port ->
                    prober.countReachable(Prober.QUICK_HOSTS, port, 3000)
                }
                quick[i] = passed
                val res = CheckResult(passed, Prober.QUICK_HOSTS.size, false, System.currentTimeMillis())
                val prev = StrategyStore.result(s)
                // Keep a thorough result unless the strategy stopped working altogether.
                if (prev == null || !prev.thorough || passed == 0) StrategyStore.setResult(s, res)
                _state.value = _state.value.copy(done = i + 1, found = quick.count { it > 0 })
            }

            val finalists = strategies.indices.filter { quick[it] > 0 }.sortedByDescending { quick[it] }.take(FINALISTS)
            var best: String? = null
            var bestPassed = 0
            if (!finishEarly && isActive && finalists.isNotEmpty()) {
                _state.value = _state.value.copy(finalPhase = true, done = 0, total = finalists.size)
                for ((n, i) in finalists.withIndex()) {
                    if (!isActive || finishEarly) break
                    val s = strategies[i]
                    _state.value = _state.value.copy(current = s, done = n)
                    val passed = prober.withStrategy(s, 0) { port ->
                        prober.countReachable(Prober.FULL_HOSTS, port, 5000)
                    }
                    StrategyStore.setResult(s, CheckResult(passed, Prober.FULL_HOSTS.size, true, System.currentTimeMillis()))
                    if (passed > bestPassed) { bestPassed = passed; best = s }
                    _state.value = _state.value.copy(done = n + 1)
                }
            }
            if (best == null) best = strategies.indices.filter { quick[it] > 0 }.maxByOrNull { quick[it] }?.let { strategies[it] }

            if (best != null) {
                Control.applyStrategy(app, best, Network.currentKind(app))
                Settings.setupDone = true
            }
            AppLog.i(TAG, "Scan finished, best: ${best ?: "none"}")
            _state.value = _state.value.copy(running = false, current = null, best = best, finishedAt = System.currentTimeMillis())
            Notifications.scanFinished(app, best != null)
            if (resumeAfter) Control.startBypass(app)
        }
    }

    /** Stops after the current strategy and applies the best one found so far. */
    fun finishNow() {
        finishEarly = true
        prober.abort()
    }

    fun cancel() {
        finishEarly = true
        prober.abort()
        job?.cancel()
        _state.value = _state.value.copy(running = false, current = null)
    }

    /** Detailed check of one strategy (YouTube/Discord reachability and latency). */
    fun probe(context: Context, strategy: String, onDone: (ProbeResult) -> Unit = {}) {
        if (busy) return
        val app = context.applicationContext
        _state.value = _state.value.copy(probing = strategy)
        App.scope.launch {
            val resume = Control.stopBypassForTesting(app)
            val result = try {
                prober.probe(strategy)
            } finally {
                _state.value = _state.value.copy(probing = null)
            }
            StrategyStore.setProbe(strategy, result)
            val total = result.youtubeTotal + result.discordTotal
            val ok = result.youtubeOk + result.discordOk
            StrategyStore.setResult(strategy, CheckResult(ok, total, false, System.currentTimeMillis()).let {
                StrategyStore.result(strategy)?.takeIf { prev -> prev.thorough && ok > 0 } ?: it
            })
            if (resume) Control.startBypass(app)
            onDone(result)
        }
    }
}
