package theodolite.benchmark

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.quarkus.runtime.annotations.RegisterForReflection
import mu.KotlinLogging
import theodolite.k8s.K8sManager
import theodolite.k8s.resourceLoader.K8sResourceLoader
import theodolite.patcher.PatcherFactory
import theodolite.util.*


private val logger = KotlinLogging.logger {}

private var DEFAULT_NAMESPACE = "default"
private var DEFAULT_THEODOLITE_APP_RESOURCES = "./benchmark-resources"

/**
 * Represents a benchmark in Kubernetes. An example for this is the BenchmarkType.yaml
 * Contains a of:
 * - [name] of the benchmark,
 * - [appResource] list of the resources that have to be deployed for the benchmark,
 * - [loadGenResource] resource that generates the load,
 * - [resourceTypes] types of scaling resources,
 * - [loadTypes] types of loads that can be scaled for the benchmark,
 * - [kafkaConfig] for the [theodolite.k8s.TopicManager],
 * - [namespace] for the client,
 * - [path] under which the resource yamls can be found.
 *
 *  This class is used for the parsing(in the [theodolite.execution.TheodoliteStandalone]) and
 *  for the deserializing in the [theodolite.execution.operator.TheodoliteOperator].
 * @constructor construct an empty Benchmark.
 */
@JsonDeserialize
@RegisterForReflection
class KubernetesBenchmark : KubernetesResource, Benchmark {
    lateinit var name: String
    lateinit var resourceTypes: List<TypeName>
    lateinit var loadTypes: List<TypeName>
    var kafkaConfig: KafkaConfig? = null
    lateinit var infrastructure: Resources
    lateinit var sut: Resources
    lateinit var loadGenerator: Resources
    private var namespace = System.getenv("NAMESPACE") ?: DEFAULT_NAMESPACE

    @Transient
    private var client: NamespacedKubernetesClient = DefaultKubernetesClient().inNamespace(namespace)

    /**
     * Loads [KubernetesResource]s.
     * It first loads them via the [YamlParserFromFile] to check for their concrete type and afterwards initializes them using
     * the [K8sResourceLoader]
     */
    fun loadKubernetesResources(resourceSet: List<ResourceSets>): Collection<Pair<String, KubernetesResource>> {
        return resourceSet.flatMap { it.loadResourceSet(this.client) }
    }

    override fun setupInfrastructure() {
        this.infrastructure.beforeActions.forEach { it.exec(client = client) }
        val kubernetesManager = K8sManager(this.client)
        loadKubernetesResources(this.infrastructure.resources)
            .map{it.second}
            .forEach { kubernetesManager.deploy(it) }
    }

    override fun teardownInfrastructure() {
        val kubernetesManager = K8sManager(this.client)
        loadKubernetesResources(this.infrastructure.resources)
            .map{it.second}
            .forEach { kubernetesManager.remove(it) }
        this.infrastructure.afterActions.forEach { it.exec(client = client) }
    }

    /**
     * Builds a deployment.
     * First loads all required resources and then patches them to the concrete load and resources for the experiment.
     * Afterwards patches additional configurations(cluster depending) into the resources.
     * @param load concrete load that will be benchmarked in this experiment.
     * @param res concrete resource that will be scaled for this experiment.
     * @param configurationOverrides
     * @return a [BenchmarkDeployment]
     */
    override fun buildDeployment(
        load: LoadDimension,
        res: Resource,
        configurationOverrides: List<ConfigurationOverride?>,
        loadGenerationDelay: Long,
        afterTeardownDelay: Long
    ): BenchmarkDeployment {
        logger.info { "Using $namespace as namespace." }

        val appResources = loadKubernetesResources(this.sut.resources)
        val loadGenResources = loadKubernetesResources(this.loadGenerator.resources)

        val patcherFactory = PatcherFactory()

        // patch the load dimension the resources
        load.getType().forEach { patcherDefinition ->
            patcherFactory.createPatcher(patcherDefinition, loadGenResources).patch(load.get().toString())
        }
        res.getType().forEach { patcherDefinition ->
            patcherFactory.createPatcher(patcherDefinition, appResources).patch(res.get().toString())
        }

        // Patch the given overrides
        configurationOverrides.forEach { override ->
            override?.let {
                patcherFactory.createPatcher(it.patcher, appResources + loadGenResources).patch(override.value)
            }
        }

        val kafkaConfig = this.kafkaConfig

        return KubernetesBenchmarkDeployment(
            sutBeforeActions = sut.beforeActions,
            sutAfterActions = sut.afterActions,
            loadGenBeforeActions = loadGenerator.beforeActions,
            loadGenAfterActions = loadGenerator.afterActions,
            appResources = appResources.map { it.second },
            loadGenResources = loadGenResources.map { it.second },
            loadGenerationDelay = loadGenerationDelay,
            afterTeardownDelay = afterTeardownDelay,
            kafkaConfig = if (kafkaConfig != null) hashMapOf("bootstrap.servers" to kafkaConfig.bootstrapServer) else mapOf(),
            topics = kafkaConfig?.topics ?: listOf(),
            client = this.client
        )
    }

    /**
     * This function can be used to set the Kubernetes client manually. This is for example necessary for testing.
     *
     * @param client
     */
    fun setClient(client: NamespacedKubernetesClient) {
        this.client = client
    }
}
