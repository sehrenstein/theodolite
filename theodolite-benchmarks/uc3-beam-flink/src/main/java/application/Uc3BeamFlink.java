package application;

import org.apache.beam.runners.flink.FlinkRunner;
import theodolite.commons.beam.AbstractBeamService;

/**
 * Implementation of the use case Aggregation based on Time Attributes using Apache Beam with the
 * Flink Runner. To run locally in standalone start Kafka, Zookeeper, the schema-registry and the
 * workload generator using the delayed_startup.sh script. And configure the Kafka, Zookeeper and
 * Schema Registry urls accordingly. Start a Flink cluster and pass its REST adress
 * using--flinkMaster as run parameter. To persist logs add
 * ${workspace_loc:/uc4-application-samza/eclipseConsoleLogs.log} as Output File under Standard
 * Input Output in Common in the Run Configuration Start via Eclipse Run.
 */
public final class Uc3BeamFlink extends AbstractBeamService {

  /**
   * Private constructor to avoid instantiation.
   */
  private Uc3BeamFlink(final String[] args) { //NOPMD
    super(args);
    this.options.setRunner(FlinkRunner.class);
  }

  /**
   * Start running this microservice.
   */
  public static void main(final String[] args) {

    final Uc3BeamFlink uc3BeamFlink = new Uc3BeamFlink(args);

    final Uc3BeamPipeline pipeline =
        new Uc3BeamPipeline(uc3BeamFlink.options, uc3BeamFlink.getConfig());

    pipeline.run().waitUntilFinish();
  }

}

