package com.neogenesis.server.infrastructure.serverless

import com.neogenesis.server.application.serverless.OutboxEventPublisher
import com.neogenesis.server.application.serverless.PublishResult
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import org.slf4j.LoggerFactory

class LoggingOutboxEventPublisher : OutboxEventPublisher {
    private val logger = LoggerFactory.getLogger(LoggingOutboxEventPublisher::class.java)

    override fun publish(event: ServerlessOutboxEvent): PublishResult {
        // Placeholder publisher to keep the outbox contract production-ready.
        // Replace with SQS/PubSub/EventBridge implementation in deployment adapters.
        logger.info(
            "Publishing outbox event id={} type={} partitionKey={} attempts={}",
            event.id,
            event.eventType,
            event.partitionKey,
            event.attempts,
        )
        return PublishResult.Success
    }
}
