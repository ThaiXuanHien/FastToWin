package com.hienthai.fastowin.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AvatarPreparationCoordinator<T>(
    private val scope: CoroutineScope,
    private val prepare: suspend (T) -> ByteArray?,
    private val onResult: (ByteArray?) -> Unit
) {
    private val selections = AvatarSelectionToken()
    private var active: Job? = null

    fun select(value: T) {
        val selection = selections.next()
        active?.cancel()
        active = scope.launch {
            val bytes = try {
                prepare(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (selections.isCurrent(selection)) onResult(bytes)
        }
    }

    fun cancel() {
        selections.invalidate()
        active?.cancel()
        active = null
    }
}
