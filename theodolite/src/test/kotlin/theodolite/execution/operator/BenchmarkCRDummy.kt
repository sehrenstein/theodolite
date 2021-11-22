package theodolite.execution.operator

import theodolite.benchmark.KubernetesBenchmark
import theodolite.model.crd.BenchmarkCRD
import theodolite.util.KafkaConfig

class BenchmarkCRDummy(name: String) {

    private val benchmark = KubernetesBenchmark()
    private val benchmarkCR = BenchmarkCRD(benchmark)

    fun getCR(): BenchmarkCRD {
        return benchmarkCR
    }

    init {
        val kafkaConfig = KafkaConfig()

        kafkaConfig.bootstrapServer = ""
        kafkaConfig.topics = emptyList()

        benchmarkCR.spec = benchmark
        benchmarkCR.metadata.name = name
        benchmarkCR.kind = "Benchmark"
        benchmarkCR.apiVersion = "v1"

        benchmark.infrastructure = emptyList()
        benchmark.appResourceSets = emptyList()
        benchmark.loadGenResourceSets = emptyList()

        benchmark.resourceTypes = emptyList()
        benchmark.loadTypes = emptyList()
        benchmark.kafkaConfig = kafkaConfig
        benchmark.name = benchmarkCR.metadata.name
    }
}