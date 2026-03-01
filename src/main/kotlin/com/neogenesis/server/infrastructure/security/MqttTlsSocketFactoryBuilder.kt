package com.neogenesis.server.infrastructure.security

import com.neogenesis.server.infrastructure.config.AppConfig
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object MqttTlsSocketFactoryBuilder {
    fun build(config: AppConfig.SecurityConfig.MtlsConfig.MqttMtlsConfig): javax.net.SocketFactory {
        require(!config.keyStorePath.isNullOrBlank()) { "MQTT mTLS keyStorePath is required" }
        require(config.keyStorePassword != null) { "MQTT mTLS keyStorePassword is required" }
        require(!config.trustStorePath.isNullOrBlank()) { "MQTT mTLS trustStorePath is required" }
        require(config.trustStorePassword != null) { "MQTT mTLS trustStorePassword is required" }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        FileInputStream(config.keyStorePath).use {
            keyStore.load(it, config.keyStorePassword.toCharArray())
        }

        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        FileInputStream(config.trustStorePath).use {
            trustStore.load(it, config.trustStorePassword.toCharArray())
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, config.keyStorePassword.toCharArray())

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)
        return sslContext.socketFactory
    }
}
