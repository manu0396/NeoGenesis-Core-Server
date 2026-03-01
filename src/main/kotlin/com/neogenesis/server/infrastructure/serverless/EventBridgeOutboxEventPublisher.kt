package com.neogenesis.server.infrastructure.serverless

import com.neogenesis.server.application.serverless.OutboxEventPublisher
import com.neogenesis.server.application.serverless.PublishResult
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.eventbridge.EventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.EventBridgeException
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry
import java.net.URI

class EventBridgeOutboxEventPublisher(
    private val eventBusName: String,
    private val sourceNamespace: String,
    region: String,
    endpointOverride: String?
) : OutboxEventPublisher, AutoCloseable {

    private val client: EventBridgeClient = EventBridgeClient.builder()
        .region(Region.of(region))
        .apply {
            if (!endpointOverride.isNullOrBlank()) {
                endpointOverride(URI.create(endpointOverride))
            }
        }
        .build()

    override fun publish(event: ServerlessOutboxEvent): PublishResult {
        return try {
            val entry = PutEventsRequestEntry.builder()
                .eventBusName(eventBusName)
                .source(sourceNamespace)
                .detailType(event.eventType)
                .detail(event.payloadJson)
                .build()

            val response = client.putEvents(
                PutEventsRequest.builder()
                    .entries(entry)
                    .build()
            )
            if (response.failedEntryCount() > 0) {
                val failed = response.entries().firstOrNull()
                val code = failed?.errorCode().orEmpty()
                val message = failed?.errorMessage().orEmpty()
                if (code.startsWith("AccessDenied", ignoreCase = true) || code.startsWith("Validation", ignoreCase = true)) {
                    PublishResult.FatalFailure("EventBridge fatal code=$code message=$message")
                } else {
                    PublishResult.RetryableFailure("EventBridge retryable code=$code message=$message")
                }
            } else {
                PublishResult.Success
            }
        } catch (error: EventBridgeException) {
            val status = error.statusCode()
            if (status in 400..499 && status != 429) {
                PublishResult.FatalFailure("EventBridge fatal status=$status message=${error.message}")
            } else {
                PublishResult.RetryableFailure("EventBridge retryable status=$status message=${error.message}")
            }
        } catch (error: SdkClientException) {
            PublishResult.RetryableFailure("EventBridge client exception: ${error.message}")
        } catch (error: Exception) {
            PublishResult.RetryableFailure("EventBridge unknown exception: ${error.message}")
        }
    }

    override fun close() = client.close()
}
