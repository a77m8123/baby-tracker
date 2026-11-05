import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    // Сборка: ./gradlew :webApp:wasmJsBrowserDistribution → webApp/build/dist/wasmJs/productionExecutable
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("malysh")
        browser {
            commonWebpackConfig {
                outputFileName = "malysh.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.babytracker.web.resources"
}
