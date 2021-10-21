package application;

import com.google.common.math.Stats;
import com.google.common.math.StatsAccumulator;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.HashMap;
import org.apache.beam.runners.flink.FlinkRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.Duration;
import titan.ccp.model.records.ActivePowerRecord;

/**
 * Implementation of the use case Downsampling using Apache Beam with the Flink Runner. To execute
 * locally in standalone start Kafka, Zookeeper, the schema-registry and the workload generator
 * using the delayed_startup.sh script. Start a Flink cluster and pass its REST adress
 * using--flinkMaster as run parameter.
 */
public final class Uc2ApplicationBeam {
  private static final String JOB_NAME = "Uc2Application";
  private static final String BOOTSTRAP = "KAFKA_BOOTSTRAP_SERVERS";
  private static final String INPUT = "INPUT";
  private static final String OUTPUT = "OUTPUT";
  private static final String SCHEMA_REGISTRY = "SCHEMA_REGISTRY_URL";
  private static final String YES = "true";
  private static final String USE_AVRO_READER = YES;
  private static final String AUTO_COMMIT_CONFIG = YES;
  private static final String KAFKA_WINDOW_DURATION_MINUTES  = "KAFKA_WINDOW_DURATION_MINUTES";

  /**
   * Private constructor to avoid instantiation.
   */
  private Uc2ApplicationBeam() {
    throw new UnsupportedOperationException();
  }

  /**
   * Start running this microservice.
   */
  @SuppressWarnings({"serial", "unchecked", "rawtypes"})
  public static void main(final String[] args) {

    // Set Configuration for Windows
    final int windowDurationMinutes = Integer.parseInt(
        System.getenv(KAFKA_WINDOW_DURATION_MINUTES) == null
            ? "1"
            : System.getenv(KAFKA_WINDOW_DURATION_MINUTES));
    final Duration duration = Duration.standardMinutes(windowDurationMinutes);

    // Set Configuration for Kafka
    final String bootstrapServer =
        System.getenv(BOOTSTRAP) == null ? "my-confluent-cp-kafka:9092"
            : System.getenv(BOOTSTRAP);
    final String inputTopic = System.getenv(INPUT) == null ? "input" : System.getenv(INPUT);
    final String outputTopic = System.getenv(OUTPUT) == null ? "output" : System.getenv(OUTPUT);
    final String schemaRegistryUrl =
        System.getenv(SCHEMA_REGISTRY) == null ? "http://my-confluent-cp-schema-registry:8081"
        : System.getenv(SCHEMA_REGISTRY);

    // Set consumer configuration for the schema registry and commits back to Kafka
    final HashMap<String, Object> consumerConfig = new HashMap<>();
    consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, AUTO_COMMIT_CONFIG);
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerConfig.put("schema.registry.url", schemaRegistryUrl);
    consumerConfig.put("specific.avro.reader", USE_AVRO_READER);
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "ucaplication");

    // Create Pipeline Options from args.
    final PipelineOptions options = PipelineOptionsFactory.fromArgs(args).create();
    options.setJobName(JOB_NAME);
    options.setRunner(FlinkRunner.class);


    final Pipeline pipeline = Pipeline.create(options);
    final CoderRegistry cr = pipeline.getCoderRegistry();

    // Set Coders for Classes that will be distributed
    cr.registerCoderForClass(ActivePowerRecord.class, AvroCoder.of(ActivePowerRecord.SCHEMA$));
    cr.registerCoderForClass(StatsAggregation.class,
        SerializableCoder.of(StatsAggregation.class));
    cr.registerCoderForClass(StatsAccumulator.class, AvroCoder.of(StatsAccumulator.class));

    final PTransform<PBegin, PCollection<KV<String, ActivePowerRecord>>> kafka =
        KafkaIO.<String, ActivePowerRecord>read()
            .withBootstrapServers(bootstrapServer)
            .withTopic(inputTopic)
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializerAndCoder((Class) KafkaAvroDeserializer.class,
                AvroCoder.of(ActivePowerRecord.class))
            .withConsumerConfigUpdates(consumerConfig)
            .withoutMetadata();
    // Apply pipeline transformations
    // Read from Kafka
    pipeline.apply(kafka)
        // Apply a fixed window
        .apply(Window
            .<KV<String, ActivePowerRecord>>into(FixedWindows.of(duration)))
        // Aggregate per window for every key
        .apply(Combine.<String, ActivePowerRecord, Stats>perKey(
            new StatsAggregation()))
        .setCoder(KvCoder.of(StringUtf8Coder.of(), SerializableCoder.of(Stats.class)))
        // Map into correct output format
        .apply(MapElements
            .via(new SimpleFunction<KV<String, Stats>, KV<String, String>>() {
              @Override
              public KV<String, String> apply(final KV<String, Stats> kv) {
                return KV.of(kv.getKey(), kv.getValue().toString());
              }
            }))
        // Write to Kafka
        .apply(KafkaIO.<String, String>write()
            .withBootstrapServers(bootstrapServer)
            .withTopic(outputTopic)
            .withKeySerializer(StringSerializer.class)
            .withValueSerializer(StringSerializer.class));

    pipeline.run().waitUntilFinish();
  }
}
