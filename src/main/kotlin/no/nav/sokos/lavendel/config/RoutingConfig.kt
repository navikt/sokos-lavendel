package no.nav.sokos.lavendel.config

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing

fun Application.routingConfig(
    useAuthentication: Boolean,
    applicationState: ApplicationState,
) {
    routing {
        internalNaisRoutes(applicationState)
    }
}

fun Route.authenticate(
    useAuthentication: Boolean,
    authenticationProviderId: String? = null,
    block: Route.() -> Unit,
) {
    if (useAuthentication) authenticate(authenticationProviderId) { block() } else block()
}
