package com.neogenesis.server.application.integration

import java.time.Instant

data class IntegrationEvent(
    val id: String,
    val type: String,
    val occurredAt: Instant,
    val payloadJson: String,
    val partitionKey: String,
)

interface IntegrationEventPublisher {
    fun publish(event: IntegrationEvent)
}

interface ClinicalAdapter {
    fun sendHl7(message: String)

    fun sendDicom(metadataJson: String)
}

interface DeviceCommandAdapter {
    fun dispatchCommand(commandJson: String)
}
