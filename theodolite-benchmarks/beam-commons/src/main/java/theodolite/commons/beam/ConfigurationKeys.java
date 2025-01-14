package theodolite.commons.beam;

/**
 * Keys to access configuration parameters.
 */
public final class ConfigurationKeys {
  // Common keys
  public static final String APPLICATION_NAME = "application.name";

  public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

  public static final String SCHEMA_REGISTRY_URL = "schema.registry.url";

  public static final String KAFKA_INPUT_TOPIC = "kafka.input.topic";

  // Additional topics
  public static final String KAFKA_FEEDBACK_TOPIC = "kafka.feedback.topic";

  public static final String KAFKA_OUTPUT_TOPIC = "kafka.output.topic";

  public static final String KAFKA_CONFIGURATION_TOPIC = "kafka.configuration.topic";

  // UC2
  public static final String KAFKA_WINDOW_DURATION_MINUTES = "kafka.window.duration.minutes";

  // UC3
  public static final String AGGREGATION_DURATION_DAYS = "aggregation.duration.days";

  public static final String AGGREGATION_ADVANCE_DAYS = "aggregation.advance.days";

  // UC4
  public static final String GRACE_PERIOD_MS = "grace.period.ms";


  // BEAM
  public static final String ENABLE_AUTO_COMMIT_CONFIG = "enable.auto.commit.config";

  public static final String AUTO_OFFSET_RESET_CONFIG = "auto.offset.reset.config";

  public static final String SPECIFIC_AVRO_READER = "specific.avro.reader";

  public static final String TRIGGER_INTERVAL  = "trigger.interval";


  private ConfigurationKeys() {
  }

}
