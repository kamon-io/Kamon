# Kamon OpenTelemetry Exporter
The exporter currently only provides a OpenTelemetry (OTLP) exporter for Kamon spans (metrics to be supported)

The reporter relies on the [opentelemetry-proto](https://github.com/open-telemetry/opentelemetry-proto) library for the gRPC communication with an OpenTelemetry (OTLP) service.

## Trace Exporter
Converts internal finished Kamon spans to OTEL proto format and exports them the to configured endpoint.

## Metrics Exporter
To be implemented