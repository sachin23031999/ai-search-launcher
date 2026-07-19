import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
    id("org.jetbrains.compose") version "1.11.1"
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Koog agent framework (LLM orchestration + MCP tool integration).
    // 0.8.0 is the newest release whose umbrella bundles MCP + all provider executors on Maven
    // Central and is built with Kotlin 2.3.10 (matches this module).
    implementation("ai.koog:koog-agents:0.8.0")
    // Transitively provided by koog, declared explicitly so we can build the stdio transport here.
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

kotlin {
    jvmToolchain(21)
}

// Dev-only: validate MCP transport + tool discovery against the .NET server without an LLM.
tasks.register<JavaExec>("mcpSmoke") {
    group = "verification"
    description = "Spawns the .NET MCP server via Koog and lists its tools."
    mainClass.set("com.ai.search.orchestrator.McpSmokeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// ---- Assembly: publish the .NET MCP server and bundle it into the app image -------------------

val dotnetExe: String = System.getenv("DOTNET_ROOT")
    ?.let { File(it, "dotnet.exe").absolutePath }
    ?: "dotnet"

val mcpServerDir = rootProject.layout.projectDirectory.dir("mcp-server")
val mcpPublishDir = layout.buildDirectory.dir("mcp-publish")

val publishMcpServer by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Publishes the .NET MCP server (self-contained win-x64) to build/mcp-publish."
    workingDir = mcpServerDir.asFile
    commandLine(
        dotnetExe, "publish", "McpServer.csproj",
        "-c", "Release", "-r", "win-x64", "--self-contained", "true",
        "-f", "net8.0-windows",
        "-o", mcpPublishDir.get().asFile.absolutePath,
    )
}

val bundleMcpServer by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Copies the published MCP server into app/resources/common/mcp-server for packaging."
    dependsOn(publishMcpServer)
    from(mcpPublishDir)
    // Compose Desktop only stages files under a platform folder (common = all OSes); files placed
    // directly in appResourcesRootDir are ignored. At runtime these are flattened into the app's
    // resources dir, so McpClient still resolves mcp-server/McpServer.exe under it.
    into(layout.projectDirectory.dir("resources/common/mcp-server"))
}

// Ensure the MCP server bundle is in place BEFORE app resources are staged into the image.
// prepareAppResources is the task that copies appResourcesRootDir into the distributable, so it
// must depend on bundleMcpServer; the create*/package* tasks depend on prepareAppResources.
tasks.matching {
    it.name == "prepareAppResources" ||
        it.name.startsWith("createDistributable") ||
        it.name.startsWith("createReleaseDistributable") ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name.startsWith("package") && it.name != "packageUberJarForCurrentOS"
}.configureEach { dependsOn(bundleMcpServer) }

compose.desktop {
    application {
        mainClass = "com.ai.search.MainKt"

        // ProGuard minification of the release image chokes on Koog/ktor's large optional
        // dependency graph (thousands of unresolved references) and aborts packaging. This is a
        // desktop launcher, so disable minification and ship the full jars for a reliable MSI.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "AISearchLauncher"
            packageVersion = "1.0.0"

            // Bundle the published .NET MCP server into the app image under resources/mcp-server.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        }
    }
}
