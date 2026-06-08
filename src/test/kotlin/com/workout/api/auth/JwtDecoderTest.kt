package com.workout.api.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import com.workout.api.TestcontainersConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

/**
 * Exercises the REAL JwtDecoder end-to-end (unlike the jwt()-mock tests): a tiny in-JVM HTTP
 * server publishes a test EC public key as JWKS, we point the resource server's jwk-set-uri at
 * it, and sign tokens with the matching private key. This is the test that would have caught the
 * M2 ES256-vs-RS256 misconfiguration (jws-algorithms + audiences come from application.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class JwtDecoderTest {
    companion object {
        private val ecJwk: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-key").generate()

        private val jwksServer: HttpServer =
            HttpServer.create(InetSocketAddress(0), 0).apply {
                val body = JWKSet(ecJwk.toPublicJWK()).toString().toByteArray()
                createContext("/jwks.json") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun jwksUri(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:${jwksServer.address.port}/jwks.json"
            }
        }

        @JvmStatic
        @AfterAll
        fun shutdown(): Unit = jwksServer.stop(0)
    }

    @Autowired
    lateinit var mvc: MockMvc

    private fun token(
        subject: String = "2e2e9f86-42aa-42ed-9abd-86d284a33998",
        audience: String = "authenticated",
        expiresInSeconds: Long = 3600,
        tamper: Boolean = false,
    ): String {
        val now = Instant.now()
        val claims =
            JWTClaimsSet
                .Builder()
                .subject(subject)
                .audience(audience)
                .issuer("https://test.local/auth/v1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .build()
        val signed =
            SignedJWT(
                JWSHeader
                    .Builder(JWSAlgorithm.ES256)
                    .keyID(ecJwk.keyID)
                    .type(JOSEObjectType.JWT)
                    .build(),
                claims,
            ).apply { sign(ECDSASigner(ecJwk)) }
        val serialized = signed.serialize()
        return if (tamper) serialized.dropLast(4) + "AAAA" else serialized
    }

    private fun callMe(token: String) = mvc.get("/api/me") { header("Authorization", "Bearer $token") }

    @Test
    fun `a valid ES256 token from our JWKS is accepted`() {
        callMe(token()).andExpect {
            status { isOk() }
            jsonPath("$.userId") { value("2e2e9f86-42aa-42ed-9abd-86d284a33998") }
        }
    }

    @Test
    fun `a tampered signature is rejected`() {
        callMe(token(tamper = true)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token for the wrong audience is rejected`() {
        callMe(token(audience = "someone-else")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an expired token is rejected`() {
        callMe(token(expiresInSeconds = -60)).andExpect { status { isUnauthorized() } }
    }
}
