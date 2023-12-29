# Observability 
> https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#actuator.observability

Caracteristica propias de este proyecto:
- Tipo de instrumentación → **Manual**. (Es decir se utilizó la libreria micrometer y micrometer-traicing + bridge con OpenTelemetry, para generar información de metricas y tracing respectivamente. Y para los logs se usó una libreria propia de OpenTelemetrySDK(Log SDK) que genera el bridge entre logback y otel.     
- Tipo deployment del collector → **As a Service** (Es decir existe un container docker corriendo el servicio de otel-collector)

Para metricas y traces, Spring Boot usa Micrometer Observation (https://micrometer.io/docs/observation).

1. OpenTelemetry Support
El modulo de spring boot actuator incluye el soporte para OpenTelemetry.
![Observability](https://github.com/estebanbri/starter-observability/blob/master/observability.png "Observability")

Nota:
Spring Boot does not provide auto-configuration for OpenTelemetry metrics or logging. 
OpenTelemetry tracing is only auto-configured when used together with Micrometer Tracing.

## Requisitos para Observabilidad
- Version minima de spring boot: Version 3.0
  - Motivo: (revisar si solo usa esta clase para sacar metricas) observabilidad con OtlpMetricsExportAutoConfiguration apareció recién en spring boot 3.0 (La cual es una clase de actuator que permite generar metricas dde tu app automaticamente, solo configurando el endpoint a traves de ella va ir exportando las metricas)

## Metricas 
> https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#actuator.metrics

Spring Boot Actuator provides dependency management and auto-configuration for Micrometer, an application metrics facade that supports numerous monitoring systems, including: OpenTelemetry, Prometheus,etc.

#### ___Paso 1___. Agregar dependencia micrometer-registry-otlp.
Having a dependency on micrometer-registry-{system} in your runtime classpath is enough for Spring Boot to configure the registry (autoconfiguration).
Esta dependencia permite generar/registrar de métricas para enviarlas a un sistema de observabilidad utilizando el formato OpenTelemetry Protocol (OTLP).
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-otlp</artifactId>
  <scope>runtime</scope>
</dependency>
```
#### ___Paso 2___: Proveer la ubicacion del endpoint de opentelemetry
By default, metrics are exported to OpenTelemetry running on your local machine. You can provide the location of the OpenTelemetry metric endpoint to use by using:
```properties
management.otlp.metrics.export.url=http://otlp.example.com:4318/v1/metrics
```
#### ___Paso 3___. Agregar configuración extra (ej: cada cuanto tiempo se va a enviar las metricas)
```properties
management.otlp.metrics.export.step=10s
```
Nota: Para debuggear si se esta exportando correctamente desde tu app. Spring boot sigue el siguiente flujo:
- OtlpMetricsExportAutoConfiguration 
- OtlpMeterRegistry.class (ponele un breakpoint al metodo publish() que es el que se llama cada vez que se exportan metricas)

## Traces 
> https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#actuator.micrometer-tracing

Spring Boot Actuator provides dependency management and auto-configuration for Micrometer Tracing, a facade for popular tracer libraries.
Nota: Para hacer este seguimiento de trazas dado un request se le va a asignar dos id's. Uno de ellos va a permanecer invariable
de principio a fin del flujo completo de llamadas este se llama 'traceId', y por otro lado el otro id va a cambiando a medida que
pasa por cada servicio y se llama 'spanId' (span en español es tramo). Entonces suponete que un request pasó por 3 servicios
para poder darle una respuesta al cliente, entonces dicho request se le asigno 1 traceId y 2 spanId durante todo su ciclo de vida
(claro solo 2 porque el primer servicio no se lo cuenta porque es como el padre que inicio el tramo).
Mas que nada el spanId se usa como puntero para saber cual fue el padre que inicio el tramo.

#### ___Paso 1___: Agregar la dependencia que lo que hace es bridges the Micrometer Observation API to OpenTelemetry.
Esta dependencia va a incluir behind the scenes toda la SDK de OpenTelemetry completa.
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
  <version>1.2.1</version>
</dependency>
```
#### ___Paso 2___: Agregar la dependencia que lo que hace es: reports traces to a collector that can accept OTLP.
```xml
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
  <version>1.31.0</version>
</dependency>
```
#### ___Paso 3___: Proveer la ubicacion del endpoint de opentelemetry
```properties
management.otlp.tracing.endpoint=http://otlp.example.com:4318/v1/traces
```
#### ___Paso 4___: Agregar configuración extra (sampling al 100%, porque por defecto spring boot solo samplea el 10% of requests para no saturar el trace backend)
```properties
management.tracing.sampling.probability=1.0
```
Nota: Micrometer Tracing le agrega el correlation Id automaticamente a cada request (aka el traceId + spanId).
#### ___Paso 5 (Optional)___: si queres un formato diferente  podes configurarlo con:
```properties
logging.pattern.correlation=[${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```
#### ___Paso 6___: Configura tu RestTemplate o WebClient
To automatically propagate traces over the network, use the auto-configured RestTemplateBuilder or WebClient.Builder to construct the client.
Importante!: If you create the WebClient or the RestTemplate without using the auto-configured builders, automatic trace propagation won’t work!
```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```
Nota: Para debuggear si se esta exportando correctamente desde tu app. Spring boot sigue el siguiente flujo:
- OtlpAutoConfiguration
- OtlpHttpSpanExporter.class (ponele un breakpoint al metodo export() que es el que se llama cada vez que se exportan spans)

## Loggers 
> https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#actuator.loggers

En resumen, Logback appender necesita fordwardear los eventos de log a la SDK Log de OpenTelemetry. Es decir
para que el OpenTelemetryAppender funcione necesitamos acceder a la instancia de OpenTelemetry. 
Esto necesita ser programaticamente durante el startup de la aplicación.

#### ___Paso 1___: Agregar la dependencia opentelemetry-logback-appender
```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>1.31.0-alpha</version>
</dependency>
```
#### ___Paso 2___: Agregar appender OpenTelemetryAppender al archivo logback.
```xml
<appender name="OpenTelemetry"
class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    <captureExperimentalAttributes>true</captureExperimentalAttributes>
    <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
</appender>
```
#### ___Paso 3___: Registrar el Appender anterior con el OpenTelemetry SDK (log bridge API / SDK) creando una clase  
En la clase la linea más importante es esta OpenTelemetryAppender.install(openTelemetrySdk) cuando al appender le registras la sdk de opentelemetry

#### ___Paso 4___: Proveer la ubicacion del endpoint de opentelemetry
```properties
otel.collector.log.url=http://localhost:4317
```
___Fuente___: https://github.com/nlinhvu/hello-service/tree/main
1. Metrics Monitoring: Spring Boot 3 -- Prometheus -- Grafana
2. Metrics Monitoring: Spring Boot 3 -- OpenTelemetry -- Prometheus -- Grafana (https://www.youtube.com/watch?v=B-ZZk4HZrfY&list=PLLMxXO6kMiNiwHCayWk74XynT5tvoMa4u&index=2)
3. Tracing Monitoring: Spring Boot 3 -- OpenTelemetry -- Jaeger -- Zipkin (https://www.youtube.com/watch?v=ducj4uR_ZoE&list=PLLMxXO6kMiNiwHCayWk74XynT5tvoMa4u&index=3)
4. Tracing Monitoring: Spring Boot 3 -- OpenTelemetry -- Grafana Tempo -- Grafana (https://www.youtube.com/watch?v=3NlGb4usCGI&list=PLLMxXO6kMiNiwHCayWk74XynT5tvoMa4u&index=4)
5. Logs Aggregation: Spring Boot 3 -- OpenTelemetry -- Loki -- Grafana (https://www.youtube.com/watch?v=UC09F-yGMG4&list=PLLMxXO6kMiNiwHCayWk74XynT5tvoMa4u&index=6)
