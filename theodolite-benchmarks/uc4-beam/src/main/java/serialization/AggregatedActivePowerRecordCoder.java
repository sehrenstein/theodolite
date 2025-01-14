package serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderException;
import titan.ccp.model.records.AggregatedActivePowerRecord;

/**
 * Wrapper Class that encapsulates a AggregatedActivePowerRecord Serde in a
 * org.apache.beam.sdk.coders.Coder.
 */
@SuppressWarnings("serial")
public class AggregatedActivePowerRecordCoder extends Coder<AggregatedActivePowerRecord>
    implements Serializable {

  private static final boolean DETERMINISTIC = true;

  private transient AvroCoder<AggregatedActivePowerRecord> avroEnCoder =
      AvroCoder.of(AggregatedActivePowerRecord.class);

  @Override
  public void encode(final AggregatedActivePowerRecord value, final OutputStream outStream)
      throws CoderException, IOException {
    if (this.avroEnCoder == null) {
      this.avroEnCoder = AvroCoder.of(AggregatedActivePowerRecord.class);
    }
    this.avroEnCoder.encode(value, outStream);

  }

  @Override
  public AggregatedActivePowerRecord decode(final InputStream inStream)
      throws CoderException, IOException {
    if (this.avroEnCoder == null) {
      this.avroEnCoder = AvroCoder.of(AggregatedActivePowerRecord.class);
    }
    return this.avroEnCoder.decode(inStream);

  }

  @Override
  public List<? extends Coder<?>> getCoderArguments() {
    return null;
  }

  @Override
  public void verifyDeterministic() throws NonDeterministicException {
    if (!DETERMINISTIC) {
      throw new NonDeterministicException(this, "This class should be deterministic!");
    }
  }
}
