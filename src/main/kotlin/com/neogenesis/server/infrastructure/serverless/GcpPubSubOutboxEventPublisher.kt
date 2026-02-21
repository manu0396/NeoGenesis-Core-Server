package com.neogenesis.server.infrastructure.serverless

import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName
import com.neogenesis.server.application.serverless.OutboxEventPublisher
import com.neogenesis.server.application.serverless.PublishResult
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit

class GcpPubSubOutboxEventPublisher(
    projectId: String,
    topicId: String,
    emulatorHost: String?
) : OutboxEventPublisher, AutoCloseable {

    private val publisher: Publisher

    init {
        val topicName = TopicName.of(projectId, topicId)
        val builder = Publisher.newBuilder(topicName)
        if (!emulatorHost.isNullOrBlank()) {
            val channel = ManagedChannelBuilder.forTarget(emulatorHost)
                .usePlaintext()
                .build()
            builder
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
        }
        publisher = builder.build()
    }

    override fun publish(event: ServerlessOutboxEvent): PublishResult {
        return try {
            val message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(event.payloadJson))
                .putAttributes("eventType", event.eventType)
                .putAttributes("partitionKey", event.partitionKey)
                .putAttributes("outboxId", event.id.toString())
                .build()
            publisher.publish(message).get(10, TimeUnit.SECONDS)
            PublishResult.Success
        } catch (error: Exception) {
            val fatal = error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                error.message?.contains("NOT_FOUND", ignoreCase = true) == true
            if (fatal) {
                PublishResult.FatalFailure("PubSub fatal error: ${error.message}")
            } else {
                PublishResult.RetryableFailure("PubSub retryable error: ${error.message}")
            }
        }
    }

    override fun close() {
        publisher.shutdown()
        publisher.awaitTermination(5, TimeUnit.SECONDS)
    }
}
