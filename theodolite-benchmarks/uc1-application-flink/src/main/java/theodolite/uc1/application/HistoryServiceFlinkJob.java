package theodolite.uc1.application;

import org.apache.commons.configuration2.Configuration;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import titan.ccp.common.configuration.Configurations;
import titan.ccp.model.records.ActivePowerRecord;

import java.util.Properties;

/**
 * The History Microservice Flink Job.
 */
public class HistoryServiceFlinkJob {

  private final Configuration config = Configurations.create();

  private void run() {
    final String applicationName = this.config.getString(ConfigurationKeys.APPLICATION_NAME);
    final String applicationVersion = this.config.getString(ConfigurationKeys.APPLICATION_VERSION);
    final String applicationId = applicationName + "-" + applicationVersion;
    final int commitIntervalMs = this.config.getInt(ConfigurationKeys.COMMIT_INTERVAL_MS);
    final String kafkaBroker = this.config.getString(ConfigurationKeys.KAFKA_BOOTSTRAP_SERVERS);
    final String inputTopic = this.config.getString(ConfigurationKeys.KAFKA_INPUT_TOPIC);
    final String schemaRegistryUrl = this.config.getString(ConfigurationKeys.SCHEMA_REGISTRY_URL);
    final boolean checkpointing = this.config.getBoolean(ConfigurationKeys.CHECKPOINTING, true);

    final Properties kafkaProps = new Properties();
    kafkaProps.setProperty("bootstrap.servers", kafkaBroker);
    kafkaProps.setProperty("group.id", applicationId);

    /*
     * final DeserializationSchema<ActivePowerRecord> serde = new
     * FlinkMonitoringRecordSerde<>(inputTopic, ActivePowerRecord.class,
     * ActivePowerRecordFactory.class);
     */

    final DeserializationSchema<ActivePowerRecord> serde =
        ConfluentRegistryAvroDeserializationSchema.forSpecific(
            ActivePowerRecord.class,
            schemaRegistryUrl);

    final FlinkKafkaConsumer<ActivePowerRecord> kafkaConsumer =
        new FlinkKafkaConsumer<>(inputTopic, serde, kafkaProps);
    kafkaConsumer.setStartFromGroupOffsets();
    if (checkpointing)
      kafkaConsumer.setCommitOffsetsOnCheckpoints(true);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    if (checkpointing)
      env.enableCheckpointing(commitIntervalMs);

    final DataStream<ActivePowerRecord> stream = env.addSource(kafkaConsumer);

    stream
        .rebalance()
        .map(v -> "ActivePowerRecord { "
            + "identifier: " + v.getIdentifier() + ", "
            + "timestamp: " + v.getTimestamp() + ", "
            + "valueInW: " + v.getValueInW() + " }")
        .print();

    try {
      env.execute(applicationId);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(final String[] args) {
    new HistoryServiceFlinkJob().run();
  }
}
