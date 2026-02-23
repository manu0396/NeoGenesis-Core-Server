package com.neogenesis.server.infrastructure.serverless

import com.neogenesis.server.application.serverless.OutboxEventPublisher
import com.neogenesis.server.infrastructure.config.AppConfig

object OutboxEventPublisherFactory {
    fun create(config: AppConfig.ServerlessConfig): OutboxEventPublisher {
        return when (config.provider.lowercase()) {
            "aws_sqs", "sqs" -> {
                val queueUrl =
                    requireNotNull(config.awsSqs.queueUrl) {
                        "neogenesis.serverless.awsSqs.queueUrl is required when provider=aws_sqs"
                    }
                AwsSqsOutboxEventPublisher(
                    queueUrl = queueUrl,
                    region = config.awsSqs.region,
                    endpointOverride = config.awsSqs.endpointOverride,
                )
            }
            "aws_eventbridge", "eventbridge" -> {
                val eventBusName =
                    requireNotNull(config.awsEventBridge.eventBusName) {
                        "neogenesis.serverless.awsEventBridge.eventBusName is required when provider=aws_eventbridge"
                    }
                EventBridgeOutboxEventPublisher(
                    eventBusName = eventBusName,
                    sourceNamespace = config.awsEventBridge.sourceNamespace,
                    region = config.awsEventBridge.region,
                    endpointOverride = config.awsEventBridge.endpointOverride,
                )
            }
            "gcp_pubsub", "pubsub" -> {
                val projectId =
                    requireNotNull(config.gcpPubSub.projectId) {
                        "neogenesis.serverless.gcpPubSub.projectId is required when provider=gcp_pubsub"
                    }
                val topicId =
                    requireNotNull(config.gcpPubSub.topicId) {
                        "neogenesis.serverless.gcpPubSub.topicId is required when provider=gcp_pubsub"
                    }
                GcpPubSubOutboxEventPublisher(
                    projectId = projectId,
                    topicId = topicId,
                    emulatorHost = config.gcpPubSub.emulatorHost,
                )
            }
            else -> LoggingOutboxEventPublisher()
        }
    }
}
