package kamon.influxdb

trait TagNormalizer {

  protected def normalize(s: String): String =
    s
      .replace(": ", "-")
      .replace(":\\", "-")
      .replace(":", "-")
      .replace(" ", "-")
      .replace("\\", "-")
      .replace("/", "-")
      .replace(".", "-")

}
