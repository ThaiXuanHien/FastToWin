package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.ClanRaceApiException
import com.hienthai.fastowin.data.network.ClanRaceGateway
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

data class ClanRaceUiState(
    val current: ClanRaceCurrent? = null,
    val standings: ClanRaceStandings? = null,
    val contributions: ClanRaceContributions? = null,
    val history: ClanRaceHistory? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val lastRefreshedAtEpochMillis: Long? = null
)

/** Publishes coherent server reads. Never derives eligibility, points or rewards. */
class ClanRaceController(private val gateway: ClanRaceGateway) {
    private data class Context(val account: String?, val clan: String?, val view: String, val tokenEpoch: String?)
    private var context: Context? = null
    private var generation = 0L
    private var requestVersion = 0L
    private var closed = false
    private val mutableState = MutableStateFlow(ClanRaceUiState())
    val state: StateFlow<ClanRaceUiState> = mutableState.asStateFlow()

    fun setContext(account: String?, clan: String?, view: String = "open", tokenEpoch: String? = null) {
        val next = Context(account, clan, view, tokenEpoch)
        if (closed || context == next) return
        context = next
        generation++
        requestVersion++
        mutableState.value = ClanRaceUiState()
    }

    suspend fun refresh(standingsPage: Int = 0, historyPage: Int = 0) {
        val owner = context ?: return
        if (closed || owner.account == null || owner.view == "closed") return
        val fence = generation
        val request = ++requestVersion
        fun valid() = !closed && generation == fence && requestVersion == request
        mutableState.value = mutableState.value.copy(isLoading = true, loadError = null)
        try {
            val current = gateway.current()
            if (!valid()) return
            val standings = gateway.standings(standingsPage)
            if (!valid()) return
            val contributions = gateway.contributions()
            if (!valid()) return
            val history = gateway.history(historyPage)
            if (!valid()) return
            // A week rollover or remote roster change between endpoints requires a fresh read.
            if (standings.weekStart != current.weekStart || contributions.weekStart != current.weekStart ||
                contributions.clanId != owner.clan) throw ClanRaceApiException("CONFLICT")
            mutableState.value = ClanRaceUiState(current, standings, contributions, history,
                lastRefreshedAtEpochMillis = current.serverNowEpochMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Ktor/Wasm browser fetch failures can be Error rather than Exception.
            if (valid()) mutableState.value = mutableState.value.copy(loadError = (error as? ClanRaceApiException)?.code ?: "NETWORK_ERROR")
        } finally {
            if (valid()) mutableState.value = mutableState.value.copy(isLoading = false)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        generation++
        mutableState.value = ClanRaceUiState()
        gateway.close()
    }
}
