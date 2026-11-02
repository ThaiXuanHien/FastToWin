package com.hienthai.fastowin.server

internal enum class RateLimitFailure { TIMEOUT, DISCONNECTED, POLICY_MISMATCH, INVALID_RESPONSE, OVERLOADED, OTHER }
internal class RateLimitUnavailableException(val reason: RateLimitFailure) :
    RuntimeException("Rate limit service temporarily unavailable.")
