package no.nav.sokos.lavendel.config

import java.net.URI
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import mu.KotlinLogging

import no.nav.sokos.lavendel.config

private val logger = KotlinLogging.logger {}
const val AUTHENTICATION_NAME = "azureAd"

fun Application.securityConfig() {
    // nødvendig fordi application ikke kan nåes inni jwt{}
    val config = config()
    if (!config.securityProperties.useAuthentication) return

    val openIdMetadata: OpenIdMetadata = wellKnowConfig(config.securityProperties.azureAdProperties.wellKnownUrl)
    val jwkProvider = cachedJwkProvider(openIdMetadata.jwksUri)

    authentication {
        jwt(AUTHENTICATION_NAME) {
            realm = config.applicationProperties.naisAppName
            verifier(
                jwkProvider = jwkProvider,
                issuer = openIdMetadata.issuer,
            ) { acceptLeeway(1) }
            validate { credential ->
                try {
                    requireNotNull(credential.payload.audience) {
                        logger.info("Auth: Missing audience in token")
                        "Auth: Missing audience in token"
                    }
                    require(credential.payload.audience.contains(config().securityProperties.azureAdProperties.clientId)) {
                        logger.info("Auth: Valid audience not found in claims")
                        "Auth: Valid audience not found in claims"
                    }
                    JWTPrincipal(credential.payload)
                } catch (e: Exception) {
                    logger.warn(e) { "Client authentication failed" }
                    null
                }
            }
        }
    }
}

private fun cachedJwkProvider(jwksUri: String): JwkProvider =
    JwkProviderBuilder(URI(jwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
        .rateLimited(
            10,
            1,
            TimeUnit.MINUTES,
        ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .build()

@Serializable
data class OpenIdMetadata(
    @SerialName("jwks_uri") val jwksUri: String,
    @SerialName("issuer") val issuer: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
)

private fun wellKnowConfig(wellKnownUrl: String): OpenIdMetadata {
    val openIdMetadata: OpenIdMetadata by lazy {
        runBlocking { httpClient.get(wellKnownUrl).body() }
    }
    return openIdMetadata
}
