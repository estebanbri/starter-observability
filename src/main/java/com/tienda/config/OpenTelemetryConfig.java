package com.tienda.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OpenTelemetryConfig {

    @Value("${otel.collector.log.url}")
    private String openTelemetryCollectorLogEndpoint;

    @Bean
    OpenTelemetry openTelemetry(SdkLoggerProvider sdkLoggerProvider, SdkTracerProvider sdkTracerProvider, ContextPropagators contextPropagators) {
        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setLoggerProvider(sdkLoggerProvider)
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(contextPropagators)
                .build();
        // Find OpenTelemetryAppender in logback configuration and install openTelemetrySdk
        OpenTelemetryAppender.install(openTelemetrySdk);
        return openTelemetrySdk;
    }

    @Bean
    SdkLoggerProvider otelSdkLoggerProvider(Environment environment, ObjectProvider<LogRecordProcessor> logRecordProcessors) {
        String applicationName = environment.getProperty("spring.application.name", "application");
        Resource springResource = Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, applicationName));
        SdkLoggerProviderBuilder builder = SdkLoggerProvider.builder()
                .setResource(Resource.getDefault().merge(springResource));
        logRecordProcessors.orderedStream().forEach(builder::addLogRecordProcessor);
        return builder.build();
    }

    @Bean
    LogRecordProcessor otelLogRecordProcessor() {
        return BatchLogRecordProcessor
                .builder(
                        OtlpGrpcLogRecordExporter.builder()
                                .setEndpoint(this.openTelemetryCollectorLogEndpoint)
                                .build())
                .build();
    }

//    @Bean
//    public SdkTracerProvider sdkTracerProvider() {
//        return SdkTracerProvider.builder()
//                // Configuración adicional aquí, si es necesario
//                .build();
//    }
//
//    @Bean
//    public ContextPropagators contextPropagators() {
//        return ContextPropagators.create(W3CTraceContextPropagator.getInstance());
//    }
}
