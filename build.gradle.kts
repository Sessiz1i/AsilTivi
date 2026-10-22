import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.wrapper {
    gradleVersion = "8.11.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
    // The installed distribution was verified locally; do not fetch it again.
    validateDistributionUrl = false
}

// Only the Android debug compile/runtime graphs and contributing common metadata.
fun isK1Configuration(name: String): Boolean = name in setOf(
    "debugCompileClasspath", "debugRuntimeClasspath",
    "androidDebugCompileClasspath", "androidDebugRuntimeClasspath",
    "commonMainResolvableDependenciesMetadata",
)

subprojects {
    configurations.configureEach {
        if (isK1Configuration(name)) { resolutionStrategy.activateDependencyLocking() }
    }
}

tasks.register("k1DependencyInventory") {
    group = "verification"
    description = "Resolve and inventory only K1 debug and contributing common graphs."
    doLast {
        val output = layout.buildDirectory.dir("reports/k1").get().asFile
        output.mkdirs()
        val coordinates = mutableListOf("project\tconfiguration\tcomponent")
        val artifacts = mutableListOf("project\tconfiguration\tmodule\tfile")
        val edges = mutableListOf("project\tconfiguration\tfrom\trequested\tselected")
        val configurationsUsed = mutableListOf("project\tconfiguration")
        subprojects.sortedBy { it.path }.forEach { module ->
            module.configurations.filter { it.isCanBeResolved && isK1Configuration(it.name) }
                .sortedBy { it.name }.forEach { configuration ->
                    configurationsUsed += "${module.path}\t${configuration.name}"
                    configuration.incoming.resolutionResult.allComponents.forEach {
                        coordinates += "${module.path}\t${configuration.name}\t${it.id.displayName}"
                    }
                    configuration.incoming.resolutionResult.allDependencies.forEach {
                        check(it is ResolvedDependencyResult) { "Unresolved K1 dependency: $it" }
                        edges += "${module.path}\t${configuration.name}\t${it.from.id.displayName}\t${it.requested.displayName}\t${it.selected.id.displayName}"
                    }
                    // Keep project nodes/edges above, but don't request their ambiguous
                    // Android lint/manifest/classes secondary artifact variants.
                    configuration.incoming.artifactView {
                        componentFilter { it is ModuleComponentIdentifier }
                    }.artifacts.artifacts.forEach {
                        artifacts += "${module.path}\t${configuration.name}\t${it.id.componentIdentifier.displayName}\t${it.file.absolutePath}"
                    }
                }
        }
        output.resolve("resolved-components.tsv").writeText((coordinates.take(1) + coordinates.drop(1).sorted()).joinToString("\n") + "\n")
        output.resolve("resolved-artifacts.tsv").writeText((artifacts.take(1) + artifacts.drop(1).sorted()).joinToString("\n") + "\n")
        output.resolve("resolved-dependency-edges.tsv").writeText((edges.take(1) + edges.drop(1).sorted()).joinToString("\n") + "\n")
        output.resolve("locked-configurations.tsv").writeText(configurationsUsed.joinToString("\n") + "\n")
        println("K1 dependency inventory: ${output.absolutePath}")
        configurationsUsed.drop(1).forEach { println("K1_LOCK_SCOPE $it") }
    }
}
