package com.neogenesis.gateway

import com.neogenesis.gateway.queue.QueueItem
import com.neogenesis.gateway.queue.QueueItemType
import com.neogenesis.grpc.GatewayRunEvent
import com.neogenesis.grpc.GatewayTelemetry
import com.google.protobuf.util.JsonFormat
import com.neogenesis.grpc.GatewayRunEventKt
import com.neogenesis.grpc.GatewayTelemetryKt

object QueueSerializer {
    fun serializeTelemetry(telemetry: GatewayTelemetry): String {
        return JsonFormat.printer().print(telemetry)
    }

    fun serializeRunEvent(event: GatewayRunEvent): String {
        return JsonFormat.printer().print(event)
    }

    fun deserialize(item: QueueItem): Any {
        return when (item.type) {
            QueueItemType.TELEMETRY -> {
                val builder = GatewayTelemetry.newBuilder()
                JsonFormat.parser().merge(item.payload, builder)
                builder.build()
            }
            QueueItemType.RUN_EVENT -> {
                val builder = GatewayRunEvent.newBuilder()
                JsonFormat.parser().merge(item.payload, builder)
                builder.build()
            }
        }
    }
}
