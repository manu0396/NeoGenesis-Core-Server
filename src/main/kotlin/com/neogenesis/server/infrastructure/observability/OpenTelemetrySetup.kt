package com.neogenesis.server.infrastructure.observability

import com.neogenesis.server.infrastructure.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor

object OpenTelemetrySetup {
    private val spanKey = AttributeKey<io.opentelemetry.api.trace.Span>("otel-span")

    fun initialize(config: AppConfig.ObservabilityConfig): OpenTelemetry? {
        val endpoint = config.otlpEndpoint ?: return null
        val exporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .build()

        val resource = Resource.getDefault().merge(
            Resource.builder()
                .put(stringKey("service.name"), config.serviceName)
                .build()
        )
        val tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()
    }

    fun Application.installHttpTracing(openTelemetry: OpenTelemetry?) {
        if (openTelemetry == null) {
            return
        }
        val tracer = openTelemetry.getTracer("neogenesis-http")
        install(
            createApplicationPlugin("OpenTelemetryHttpTracing") {
                onCall { call ->
                    val span = tracer.spanBuilder("${call.request.httpMethod.value} ${call.request.path()}")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute(stringKey("http.method"), call.request.httpMethod.value)
                        .setAttribute(stringKey("http.target"), call.request.path())
                        .startSpan()
                    call.attributes.put(spanKey, span)
                }
                onCallRespond { call, _ ->
                    call.attributes.getOrNull(spanKey)?.let { span ->
                        span.setStatus(StatusCode.OK)
                        span.end()
                    }
                }
                on(CallFailed) { call, cause ->
                    call.attributes.getOrNull(spanKey)?.let { span ->
                        span.recordException(cause)
                        span.setStatus(StatusCode.ERROR)
                        span.end()
                    }
                }
            }
        )
    }
}
