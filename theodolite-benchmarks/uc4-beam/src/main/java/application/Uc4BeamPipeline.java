package application; // NOPMD

import com.google.common.math.StatsAccumulator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.coders.SetCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.Latest;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.View;
import org.apache.beam.sdk.transforms.windowing.AfterPane;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.commons.configuration2.Configuration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;
import serialization.AggregatedActivePowerRecordCoder;
import serialization.AggregatedActivePowerRecordDeserializer;
import serialization.AggregatedActivePowerRecordSerializer;
import serialization.EventCoder;
import serialization.EventDeserializer;
import serialization.SensorParentKeyCoder;
import theodolite.commons.beam.AbstractPipeline;
import theodolite.commons.beam.ConfigurationKeys;
import theodolite.commons.beam.kafka.KafkaActivePowerTimestampReader;
import theodolite.commons.beam.kafka.KafkaGenericReader;
import theodolite.commons.beam.kafka.KafkaWriterTransformation;
import titan.ccp.configuration.events.Event;
import titan.ccp.model.records.ActivePowerRecord;
import titan.ccp.model.records.AggregatedActivePowerRecord;

/**
 * Implementation of the use case Hierarchical Aggregation using Apache Beam.
 */
public final class Uc4BeamPipeline extends AbstractPipeline {

  protected Uc4BeamPipeline(final PipelineOptions options, final Configuration config) { // NOPMD
    super(options, config);

    // Additional needed variables
    final String feedbackTopic = config.getString(ConfigurationKeys.KAFKA_FEEDBACK_TOPIC);
    final String outputTopic = config.getString(ConfigurationKeys.KAFKA_OUTPUT_TOPIC);
    final String configurationTopic = config.getString(ConfigurationKeys.KAFKA_CONFIGURATION_TOPIC);

    final Duration duration =
        Duration.standardSeconds(config.getInt(ConfigurationKeys.KAFKA_WINDOW_DURATION_MINUTES));
    final Duration triggerDelay =
        Duration.standardSeconds(config.getInt(ConfigurationKeys.TRIGGER_INTERVAL));
    final Duration gracePeriod =
        Duration.standardSeconds(config.getInt(ConfigurationKeys.GRACE_PERIOD_MS));

    // Build kafka configuration
    final Map<String, Object> consumerConfig = this.buildConsumerConfig();
    final Map<String, Object> configurationConfig = this.configurationConfig(config);

    // Set Coders for Classes that will be distributed
    final CoderRegistry cr = this.getCoderRegistry();
    registerCoders(cr);

    // Read from Kafka
    // ActivePowerRecords
    final KafkaActivePowerTimestampReader kafkaActivePowerRecordReader =
        new KafkaActivePowerTimestampReader(this.bootstrapServer, this.inputTopic, consumerConfig);

    // Configuration Events
    final KafkaGenericReader<Event, String> kafkaConfigurationReader =
        new KafkaGenericReader<>(
            this.bootstrapServer, configurationTopic, configurationConfig,
            EventDeserializer.class, StringDeserializer.class);

    // Transform into AggregatedActivePowerRecords into ActivePowerRecords
    final AggregatedToActive aggregatedToActive = new AggregatedToActive();

    // Write to Kafka
    final KafkaWriterTransformation<AggregatedActivePowerRecord> kafkaOutput =
        new KafkaWriterTransformation<>(
            this.bootstrapServer, outputTopic, AggregatedActivePowerRecordSerializer.class);

    final KafkaWriterTransformation<AggregatedActivePowerRecord> kafkaFeedback =
        new KafkaWriterTransformation<>(
            this.bootstrapServer, feedbackTopic, AggregatedActivePowerRecordSerializer.class);

    // Apply pipeline transformations
    final PCollection<KV<String, ActivePowerRecord>> values = this
        .apply("Read from Kafka", kafkaActivePowerRecordReader)
        .apply("Read Windows", Window.into(FixedWindows.of(duration)))
        .apply("Set trigger for input", Window
            .<KV<String, ActivePowerRecord>>configure()
            .triggering(Repeatedly.forever(
                AfterProcessingTime.pastFirstElementInPane()
                    .plusDelayOf(triggerDelay)))
            .withAllowedLateness(gracePeriod)
            .discardingFiredPanes());

    // Read the results of earlier aggregations.
    final PCollection<KV<String, ActivePowerRecord>> aggregationsInput = this
        .apply("Read aggregation results", KafkaIO.<String, AggregatedActivePowerRecord>read()
            .withBootstrapServers(this.bootstrapServer)
            .withTopic(feedbackTopic)
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializer(AggregatedActivePowerRecordDeserializer.class)
            .withTimestampPolicyFactory(
                (tp, previousWaterMark) -> new AggregatedActivePowerRecordEventTimePolicy(
                    previousWaterMark))
            .withoutMetadata())
        .apply("Apply Windows", Window.into(FixedWindows.of(duration)))
        // Convert into the correct data format
        .apply("Convert AggregatedActivePowerRecord to ActivePowerRecord",
            MapElements.via(aggregatedToActive))
        .apply("Set trigger for feedback", Window
            .<KV<String, ActivePowerRecord>>configure()
            .triggering(Repeatedly.forever(
                AfterProcessingTime.pastFirstElementInPane()
                    .plusDelayOf(triggerDelay)))
            .withAllowedLateness(gracePeriod)
            .discardingFiredPanes());

    // Prepare flatten
    final PCollectionList<KV<String, ActivePowerRecord>> collections =
        PCollectionList.of(values).and(aggregationsInput);

    // Create a single PCollection out of the input and already computed results
    final PCollection<KV<String, ActivePowerRecord>> inputCollection =
        collections.apply("Flatten sensor data and aggregation results",
            Flatten.pCollections());

    // Build the configuration stream from a changelog.
    final PCollection<KV<String, Set<String>>> configurationStream = this
        .apply("Read sensor groups", kafkaConfigurationReader)
        // Only forward relevant changes in the hierarchy
        .apply("Filter changed and status events",
            Filter.by(new FilterEvents()))
        // Build the changelog
        .apply("Generate Parents for every Sensor", ParDo.of(new GenerateParentsFn()))
        .apply("Update child and parent pairs", ParDo.of(new UpdateChildParentPairs()))
        .apply("Set trigger for configuration", Window
            .<KV<String, Set<String>>>configure()
            .triggering(AfterWatermark.pastEndOfWindow()
                .withEarlyFirings(
                    AfterPane.elementCountAtLeast(1)))
            .withAllowedLateness(Duration.ZERO)
            .accumulatingFiredPanes());

    final PCollectionView<Map<String, Set<String>>> childParentPairMap =
        configurationStream.apply(Latest.perKey())
            // Reset trigger to avoid synchronized processing time
            .apply("Reset trigger for configurations", Window
                .<KV<String, Set<String>>>configure()
                .triggering(AfterWatermark.pastEndOfWindow()
                    .withEarlyFirings(
                        AfterPane.elementCountAtLeast(1)))
                .withAllowedLateness(Duration.ZERO)
                .accumulatingFiredPanes())
            .apply(View.asMap());

    final FilterNullValues filterNullValues = new FilterNullValues();

    // Build pairs of every sensor reading and parent
    final PCollection<KV<SensorParentKey, ActivePowerRecord>> flatMappedValues =
        inputCollection.apply(
            "Duplicate as flatMap",
            ParDo.of(new DuplicateAsFlatMap(childParentPairMap))
                .withSideInputs(childParentPairMap))
            .apply("Filter only latest changes", Latest.perKey())
            .apply("Filter out null values",
                Filter.by(filterNullValues));

    final SetIdForAggregated setIdForAggregated = new SetIdForAggregated();
    final SetKeyToGroup setKeyToGroup = new SetKeyToGroup();

    // Aggregate for every sensor group of the current level
    final PCollection<KV<String, AggregatedActivePowerRecord>> aggregations = flatMappedValues
        .apply("Set key to group", MapElements.via(setKeyToGroup))
        // Reset trigger to avoid synchronized processing time
        .apply("Reset trigger for aggregations", Window
            .<KV<String, ActivePowerRecord>>configure()
            .triggering(Repeatedly.forever(
                AfterProcessingTime.pastFirstElementInPane()
                    .plusDelayOf(triggerDelay)))
            .withAllowedLateness(gracePeriod)
            .discardingFiredPanes())
        .apply(
            "Aggregate per group",
            Combine.perKey(new RecordAggregation()))
        .apply("Set the Identifier in AggregatedActivePowerRecord",
            MapElements.via(setIdForAggregated));

    aggregations.apply("Write to aggregation results", kafkaOutput);

    aggregations
        .apply("Write to feedback topic", kafkaFeedback);

  }


  /**
   * Builds a simple configuration for a Kafka consumer transformation.
   *
   * @return the build configuration.
   */
  public Map<String, Object> configurationConfig(final Configuration config) {
    final Map<String, Object> consumerConfig = new HashMap<>();
    consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
        config.getString(ConfigurationKeys.ENABLE_AUTO_COMMIT_CONFIG));
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        config
            .getString(ConfigurationKeys.AUTO_OFFSET_RESET_CONFIG));

    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, config
        .getString(ConfigurationKeys.APPLICATION_NAME) + "-configuration");
    return consumerConfig;
  }


  /**
   * Registers all Coders for all needed Coders.
   *
   * @param cr CoderRegistry.
   */
  private static void registerCoders(final CoderRegistry cr) {
    cr.registerCoderForClass(ActivePowerRecord.class,
        AvroCoder.of(ActivePowerRecord.class));
    cr.registerCoderForClass(AggregatedActivePowerRecord.class,
        new AggregatedActivePowerRecordCoder());
    cr.registerCoderForClass(Set.class, SetCoder.of(StringUtf8Coder.of()));
    cr.registerCoderForClass(Event.class, new EventCoder());
    cr.registerCoderForClass(SensorParentKey.class, new SensorParentKeyCoder());
    cr.registerCoderForClass(StatsAccumulator.class, AvroCoder.of(StatsAccumulator.class));
  }
}

