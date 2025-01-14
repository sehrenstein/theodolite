package theodolite.evaluation

import theodolite.benchmark.BenchmarkExecution
import theodolite.util.EvaluationFailedException
import theodolite.util.IOHandler
import theodolite.util.LoadDimension
import theodolite.util.Resource
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

/**
 * Contains the analysis. Fetches a metric from Prometheus, documents it, and evaluates it.
 * @param slo Slo that is used for the analysis.
 */
class AnalysisExecutor(
    private val slo: BenchmarkExecution.Slo,
    private val executionId: Int
) {

    private val fetcher = MetricFetcher(
        prometheusURL = slo.prometheusUrl,
        offset = Duration.ofHours(slo.offset.toLong())
    )

    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param res of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun analyze(load: LoadDimension, res: Resource, executionIntervals: List<Pair<Instant, Instant>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder: String = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load.get()}_${res.get()}_${slo.sloType.toSlug()}"

            val prometheusData = executionIntervals
                .map { interval ->
                    fetcher.fetchMetric(
                        start = interval.first,
                        end = interval.second,
                        query = SloConfigHandler.getQueryString(slo = slo)
                    )
                }

            prometheusData.forEach { data ->
                ioHandler.writeToCSVFile(
                    fileURL = "${fileURL}_${repetitionCounter++}",
                    data = data.getResultAsList(),
                    columns = listOf("labels", "timestamp", "value")
                )
            }

            val sloChecker = SloCheckerFactory().create(
                sloType = slo.sloType,
                properties = slo.properties,
                load = load
            )

            return sloChecker.evaluate(prometheusData)

        } catch (e: Exception) {
            throw EvaluationFailedException("Evaluation failed for resource '${res.get()}' and load '${load.get()}", e)
        }
    }

    private val NONLATIN: Pattern = Pattern.compile("[^\\w-]")
    private val WHITESPACE: Pattern = Pattern.compile("[\\s]")

    private fun String.toSlug(): String {
        val noWhitespace: String = WHITESPACE.matcher(this).replaceAll("-")
        val normalized: String = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD)
        val slug: String = NONLATIN.matcher(normalized).replaceAll("")
        return slug.lowercase(Locale.ENGLISH)
    }
}
