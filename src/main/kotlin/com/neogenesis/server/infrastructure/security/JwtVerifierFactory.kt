package com.neogenesis.server.infrastructure.security

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.neogenesis.server.infrastructure.config.AppConfig
import java.net.URL
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

object JwtVerifierFactory {
    fun create(
        jwtConfig: AppConfig.SecurityConfig.JwtConfig,
        secretResolver: SecretResolver
    ): JWTVerifier {
        return if (jwtConfig.mode.equals("oidc", ignoreCase = true)) {
            val jwksUrl = requireNotNull(jwtConfig.jwksUrl) {
                "neogenesis.security.jwt.jwksUrl is required when mode=oidc"
            }
            val jwkProvider = JwkProviderBuilder(URL(jwksUrl))
                .cached(20, 12, TimeUnit.HOURS)
                .rateLimited(20, 1, TimeUnit.MINUTES)
                .build()

            val keyProvider = object : RSAKeyProvider {
                override fun getPublicKeyById(keyId: String?): RSAPublicKey? {
                    if (keyId.isNullOrBlank()) {
                        return null
                    }
                    val jwk = jwkProvider.get(keyId)
                    return jwk.publicKey as? RSAPublicKey
                }

                override fun getPrivateKey(): RSAPrivateKey? = null

                override fun getPrivateKeyId(): String? = null
            }

            JWT.require(Algorithm.RSA256(keyProvider))
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .build()
        } else {
            val resolvedSecret = secretResolver.resolve(jwtConfig.secret)
                ?: error("Unable to resolve JWT secret")
            JWT.require(Algorithm.HMAC256(resolvedSecret))
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .build()
        }
    }
}
