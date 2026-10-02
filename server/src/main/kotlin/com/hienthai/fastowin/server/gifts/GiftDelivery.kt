package com.hienthai.fastowin.server.gifts

import io.ktor.server.application.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun Application.startGiftDelivery(service: GiftService) = launch {
    while (isActive) {
        try { service.deliverDueBatch() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { environment.log.warn("Gift delivery batch failed; persisted checkpoint will be retried.") }
        delay(5000)
    }
}
