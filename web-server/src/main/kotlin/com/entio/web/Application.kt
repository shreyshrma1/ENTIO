package com.entio.web

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Installs the smallest server boundary needed before semantic web contracts are added.
 */
public fun Application.module(): Unit {
    routing {
        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }

        get("/ready") {
            call.respondText("ready", ContentType.Text.Plain)
        }
    }
}
