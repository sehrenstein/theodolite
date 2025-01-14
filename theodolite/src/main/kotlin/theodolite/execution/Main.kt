package theodolite.execution

import io.quarkus.runtime.annotations.QuarkusMain
import mu.KotlinLogging
import theodolite.execution.operator.TheodoliteOperator
import theodolite.util.Configuration
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

@QuarkusMain
object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val mode = Configuration.EXECUTION_MODE
        logger.info { "Start Theodolite with mode $mode" }

        when (mode.lowercase()) {
            ExecutionModes.STANDALONE.value -> TheodoliteStandalone().start()
            ExecutionModes.OPERATOR.value -> TheodoliteOperator().start()
            else -> {
                logger.error { "MODE $mode not found" }
                exitProcess(1)
            }
        }
    }
}
