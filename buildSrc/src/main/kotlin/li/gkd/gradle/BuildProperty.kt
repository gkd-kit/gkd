package li.gkd.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider

fun Project.buildProperty(name: String): Provider<String> {
    return providers.environmentVariable(name)
        .filter { it.isNotBlank() }
        .orElse(
            providers.gradleProperty(name)
                .filter { it.isNotBlank() },
        )
}
