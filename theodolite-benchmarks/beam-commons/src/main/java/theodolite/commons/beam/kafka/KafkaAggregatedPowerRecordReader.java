package theodolite.commons.beam.kafka;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.kafka.common.serialization.StringDeserializer;
import titan.ccp.model.records.ActivePowerRecord;

/**
 * Simple {@link PTransform} that read from Kafka using {@link KafkaIO}.
 */
public class KafkaAggregatedPowerRecordReader extends
    PTransform<PBegin, PCollection<KV<String, ActivePowerRecord>>> {

  private final PTransform<PBegin, PCollection<KV<String, ActivePowerRecord>>> reader;


  /**
   * Instantiates a {@link PTransform} that reads from Kafka with the given Configuration.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public KafkaAggregatedPowerRecordReader(String bootstrapServer, String inputTopic,
                                          Map<Object, Object> consumerConfig) {
    super();

    // Check if boostrap server and inputTopic are defined
    if (bootstrapServer.isEmpty() || inputTopic.isEmpty()) {
      throw new IllegalArgumentException("bootstrapServer or inputTopic missing");
    }

    reader =
        KafkaIO.<String, ActivePowerRecord>read()
            .withBootstrapServers(bootstrapServer)
            .withTopic(inputTopic)
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializerAndCoder((Class) KafkaAvroDeserializer.class,
                AvroCoder.of(ActivePowerRecord.class))
            .withConsumerConfigUpdates(consumerConfig)
            .withoutMetadata();
  }

  @Override
  public PCollection<KV<String, ActivePowerRecord>> expand(PBegin input) {
    return input.apply(this.reader);
  }

}