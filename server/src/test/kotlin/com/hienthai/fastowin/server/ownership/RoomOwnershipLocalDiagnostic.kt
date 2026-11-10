package com.hienthai.fastowin.server.ownership

/** Local Docker entry point; the Railway smoke entry point always retains its full baseline. */
fun main() {
    val settings = RailwayRoomSmokeSettings.fromEnvironment(System.getenv())
    require(settings.baseline)
    runRailwayRoomBaseline(settings, localDiagnostic = true)
}
