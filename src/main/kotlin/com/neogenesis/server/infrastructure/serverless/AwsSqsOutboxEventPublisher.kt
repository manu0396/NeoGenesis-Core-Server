package com.neogenesis.server.infrastructure.serverless

import com.neogenesis.server.application.serverless.OutboxEventPublisher
import com.neogenesis.server.application.serverless.PublishResult
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SqsException
import java.net.URI

class AwsSqsOutboxEventPublisher(
    private val queueUrl: String,
    region: String,
    endpointOverride: String?,
) : OutboxEventPublisher, AutoCloseable {
    private val client: SqsClient =
        SqsClient.builder()
            .region(Region.of(region))
            .apply {
                if (!endpointOverride.isNullOrBlank()) {
                    endpointOverride(URI.create(endpointOverride))
                }
            }
            .build()

    override fun publish(event: ServerlessOutboxEvent): PublishResult {
        return try {
            val baseBuilder =
                SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(event.payloadJson)
                    .messageAttributes(
                        mapOf(
                            "eventType" to
                                software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.eventType)
                                    .build(),
                            "partitionKey" to
                                software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.partitionKey)
                                    .build(),
                        ),
                    )

            if (queueUrl.endsWith(".fifo", ignoreCase = true)) {
                baseBuilder.messageGroupId(event.partitionKey.ifBlank { "default-group" })
                baseBuilder.messageDeduplicationId("${event.eventType}:${event.id}:${event.attempts}")
            }

            val request = baseBuilder.build()

            client.sendMessage(request)
            PublishResult.Success
        } catch (error: SqsException) {
            val code = error.statusCode()
            if (code in 400..499 && code != 429) {
                PublishResult.FatalFailure("SQS fatal status=$code message=${error.message}")
            } else {
                PublishResult.RetryableFailure("SQS retryable status=$code message=${error.message}")
            }
        } catch (error: SdkClientException) {
            PublishResult.RetryableFailure("SQS client exception: ${error.message}")
        } catch (error: Exception) {
            PublishResult.RetryableFailure("SQS unknown exception: ${error.message}")
        }
    }

    override fun close() {
        client.close()
    }
}
