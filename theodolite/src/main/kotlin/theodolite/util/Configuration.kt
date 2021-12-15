package theodolite.util

import theodolite.execution.ExecutionModes

// Defaults
private const val DEFAULT_NAMESPACE = "default"
private const val DEFAULT_COMPONENT_NAME = "theodolite-operator"


class Configuration {
    companion object {
        val NAMESPACE = System.getenv("NAMESPACE") ?: DEFAULT_NAMESPACE
        val COMPONENT_NAME = System.getenv("COMPONENT_NAME") ?: DEFAULT_COMPONENT_NAME
        val EXECUTION_MODE = System.getenv("MODE") ?: ExecutionModes.STANDALONE.value
    }

}
