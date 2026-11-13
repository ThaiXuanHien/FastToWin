package com.hienthai.fastowin.server.ownership

import java.io.OutputStream
import java.io.PrintStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.system.exitProcess

/** Test-image entrypoint. Receipt contains only the bound manifest and run identity. */
internal fun runProductionSeed(
    args: Array<String>,
    values: Map<String, String>,
    out: PrintStream,
    seed: (RoomOwnershipProductionSeedSettings) -> OwnedAdmissionPolicy = { settings ->
        seedRoomOwnershipProductionAccounts(settings.dataSource(), settings)
    }
): Int {
    return try {
        require(args.contentEquals(arrayOf("--seed-production")))
        val settings = RoomOwnershipProductionSeedSettings.fromEnvironment(values)
        val policy = seed(settings)
        require(policy.admits(settings.admissionBinding))
        out.println("""{"productionSeeded":true,"manifestSha256":"${settings.manifestSha256}","runId":"${settings.runId}","admissionGeneration":${policy.generation},"topologySha256":"${policy.topologySha256}","policySha256":"${policy.policySha256}"}""")
        0
    } catch (_: Exception) {
        out.println("Production rehearsal seed failed")
        1
    }
}

fun main(args: Array<String>) {
    val output = System.out
    val error = System.err
    val sink = PrintStream(OutputStream.nullOutputStream())
    val code = try {
        System.setOut(sink)
        System.setErr(sink)
        if (args.contentEquals(arrayOf("--seed-production"))) {
            runProductionSeed(args, System.getenv(), output)
        } else {
            runProductionWorkload(args, System.getenv(), output)
        }
    } finally {
        System.setOut(output)
        System.setErr(error)
        sink.close()
    }
    if (code != 0) exitProcess(code)
}

private fun runProductionWorkload(args: Array<String>, values: Map<String, String>, out: PrintStream): Int {
    var stage = PrivateCapabilityStage.READY
    return try {
        require(args.size == 1 && args[0] in setOf("--capability-production", "--workload-production", "--preflight-production"))
        val settings = ProductionRehearsalSettings.parse(values)
        val jar = settings.verifiedJarSha(java.nio.file.Path.of("lib/server.jar"))
        if (args[0] == "--capability-production") {
            val endpoints = settings.endpoints()
            runBlocking {
                withTimeout(120_000) {
                    RoomAcceptanceFixture(settings.acceptance(endpoints), settings.control(endpoints)).use { fixture ->
                        fixture.verifyPrivateCapabilities(endpoints, settings.redisPassword) { stage = it }
                    }
                }
            }
            out.println("""{"productionCapabilityVerified":true,"manifestSha256":"${settings.manifestSha}","profile":"${settings.profile}","jarSha256":"$jar"}""")
        } else {
            runRehearsal(settings, out, jarSha256 = jar, production = true, preflightOnly = args[0] == "--preflight-production")
        }
        0
    } catch (_: Exception) {
        if (args.contentEquals(arrayOf("--capability-production"))) {
            out.println("Production capability failed: " + stage.name)
        } else out.println("Production rehearsal failed")
        1
    }
}
