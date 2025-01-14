package application;

import org.apache.beam.runners.samza.SamzaRunner;
import theodolite.commons.beam.AbstractBeamService;

/**
 * Implementation of the use case Database Storage using Apache Beam with the Samza Runner. To
 * execute locally in standalone start Kafka, Zookeeper, the schema-registry and the workload
 * generator. Add
 * --configFactory=org.apache.samza.config.factories.PropertiesConfigFactory
 * --configFilePath=samza-standalone.properties
 * --samzaExecutionEnvironment=STANDALONE --maxSourceParallelism=1024 as program arguments. To
 * persist logs add ${workspace_loc:/uc4-application-samza/eclipseConsoleLogs.log} as Output File
 * under Standard Input Output in Common in the Run Configuration Start via Eclipse Run.
 */
public final class Uc1BeamSamza extends AbstractBeamService {

  /**
   * Private constructor setting specific options for this use case.
   */
  private Uc1BeamSamza(final String[] args) { //NOPMD
    super(args);
    this.options.setRunner(SamzaRunner.class);
  }

  /**
   * Main method.
   */
  public static void main(final String[] args) {

    // Create application via configurations
    final Uc1BeamSamza uc1 = new Uc1BeamSamza(args);

    // Create pipeline with configurations
    final Uc1BeamPipeline pipeline = new Uc1BeamPipeline(uc1.options, uc1.getConfig());

    // Submit job and start execution
    pipeline.run().waitUntilFinish();
  }
}



