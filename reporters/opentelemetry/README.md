# Kamon OpenTelemetry Exporter
The exporter currently only provides a OpenTelemetry (OTLP) exporter for Kamon spans (metrics to be supported)

The reporter relies on the http/protobuf and grpc exporter modules from the [opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java) project

## Trace Exporter
Converts internal finished Kamon spans to OTEL proto format and exports them to the configured endpoint. See reference.conf for available configuration options

## Metrics Exporter
To be implemented
