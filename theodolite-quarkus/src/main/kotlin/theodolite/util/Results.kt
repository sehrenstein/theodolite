package theodolite.util

import theodolite.util.LoadDimension
import theodolite.util.Resource
import kotlin.math.exp

class Results {
    // load, instances
    private val results: MutableMap<Pair<LoadDimension, Resource>, Boolean> = mutableMapOf() // multi map guava

    public fun setResult(experiment: Pair<LoadDimension, Resource>, successful: Boolean) {
        this.results.put(experiment, successful)
    }

    public fun getResult (experiment: Pair<LoadDimension, Resource>): Boolean? {
        return this.results.get(experiment)
    }

    public fun getMinRequiredInstances(load: LoadDimension?): Resource? {
        if (this.results.isEmpty()) return Resource(Int.MIN_VALUE)

        var requiredInstances: Resource? = Resource(Int.MAX_VALUE)
        for(experiment in results) {
            if(experiment.key.first == load && experiment.value){
                if(requiredInstances == null) {
                    requiredInstances = experiment.key.second
                }else if (experiment.key.second.get() < requiredInstances.get()) {
                    requiredInstances = experiment.key.second
                }
            }
        }
        return requiredInstances
    }

    public fun getMaxBenchmarkedLoad(load: LoadDimension): LoadDimension? {
        var maxBenchmarkedLoad: LoadDimension? = null;
        for(experiment in results) {
            if (experiment.value) {
                if(experiment.key.first.get() <= load.get()) {
                    if (maxBenchmarkedLoad == null) {
                        maxBenchmarkedLoad = experiment.key.first
                    } else if (maxBenchmarkedLoad.get() < experiment.key.first.get()) {
                        maxBenchmarkedLoad = experiment.key.first
                    }
                }
            }
        }
        return maxBenchmarkedLoad
    }
}