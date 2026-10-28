// The Stein Loader build.
//
//   ./gradlew build           build/libs/steinloader.jar (goes on the game's classpath), the API jar for mod authors
//                             (build/libs/stein-api.jar and -sources), the test mod, and every check: ModPatch = Patcher,
//                             API references and compatibility, access and dependency tests, coverage, preflight
//   ./gradlew runClient       plays the game through the Stein Bridge with this Loader (and -Pstein.mods=<folder>)
//   ./gradlew verify -Pstein.world=<world>
//                             plays a singleplayer world with every verification mode on, in both pipelines
//   ./gradlew spotlessApply   formats the Java code (google-java-format, AOSP style)
//
// Written with MCP's readable names (the same as the SDK's: Minecraft.getMinecraft()), the Loader goes through:
//   1. javac against the game jar with readable names, and NamedBuild tosrg, which takes it to the SRG names;
//   2. ModRemap (from the Bridge): obfuscated 1.8.9 names, checking that every reference to the game resolves;
//   3. the Patcher (from the Bridge) with the game rules (LWJGL 3, matrix stack, batching) plus the batch hooks before
//      the GL calls. Mods skip this step: the Loader applies the same rules when loading them (ModPatch);
//   4. the jar, with ASM inside. It reaches players as it is: the Loader is not obfuscated. The rules ModPatch applies to
//      mods are not in it: they travel in the Bridge jar (steinbridge/rules), so they always match the Bridge that runs.
import java.io.OutputStream
import javax.inject.Inject
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

plugins {
    java
    alias(libs.plugins.spotless)
}

/** File operations for task actions: Task.project is not available while a task runs. */
interface FileOps {
    @get:Inject val fs: FileSystemOperations
}
val fileOps = objects.newInstance<FileOps>().fs

group = "dev.xavier.stein"
version = Regex("""String VERSION = "([^"]+)"""")
    .find(file("src/main/java/dev/xavier/stein/loader/Loader.java").readText())!!.groupValues[1]

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
}

// ---- the Bridge and the SDK ----

val bridgeBuild = gradle.includedBuild("stein-bridge")
val bridgeDir = bridgeBuild.projectDir
val bridgeTools = files(bridgeDir.resolve("build/classes/java/tools"))
val patcherClasses = files(bridgeDir.resolve("build/classes/java/patcher"))
val bridgeShim = bridgeDir.resolve("build/bridge/classes")
val srgFile = bridgeDir.resolve("build/tools/joined-1.8.9.srg")
val mcpCsv = bridgeDir.resolve("build/tools/mcp-stable-22")
val namedGameJar = bridgeDir.resolve("build/work/mc-named.jar")
val gameProperties = bridgeDir.resolve("build/run/game.properties")
val nameMapping = bridgeDir.resolve("scan/name-mapping.txt")
fun bridgeRules(vararg names: String) = names.map { bridgeDir.resolve("patcher/redirects-$it.tsv") }

/** What the Bridge build wrote about the patched game (build/run/game.properties). */
fun game(key: String): String =
    Properties().apply { gameProperties.reader().use { load(it) } }.getProperty(key)
// Read when the classpath is used, not when the task graph is planned: on a fresh clone the Bridge writes the file
// during this same build (gameRuntime), after the graph exists.
fun gameClasspath() = files(provider {
    if (gameProperties.isFile) game("classpath").split(File.pathSeparator) else emptyList()
})
fun gameJar() = File(game("gameJar"))

// Everything the Loader needs from the Bridge: its jar, the patched game, the tools, the MCP names.
val bridgeReady = listOf(":assemble", ":toolsClasses", ":patcherClasses", ":testClasses", ":namedGame")
    .map { bridgeBuild.task(it) }

val asmCore: Configuration = configurations.create("asmCore") { isTransitive = false }
val asm: Configuration = configurations.create("asm")
val sdkTools: Configuration = configurations.create("sdkTools")

dependencies {
    asmCore(libs.asm)
    asm(libs.asm)
    asm(libs.asm.commons)
    asm(libs.asm.tree)
    asm(libs.asm.analysis)
    sdkTools("dev.xavier.stein:stein-sdk")
}

// ---- source sets ----

sourceSets.main {
    java.setSrcDirs(listOf("src/main/java"))
    resources.setSrcDirs(listOf("src/main/resources"))
}
val toolsSet = sourceSets.create("tools") {
    java.setSrcDirs(listOf("tools"))
    java.include("*.java")
    resources.setSrcDirs(emptyList<File>())
    compileClasspath += asm
    runtimeClasspath += asm
}
sourceSets.test {
    java.setSrcDirs(listOf("test"))
    java.exclude("mods/**")
    resources.setSrcDirs(emptyList<File>())
}
val hooktestSet = sourceSets.create("hooktest") {
    java.setSrcDirs(listOf("test/mods/hooktest/src"))
    resources.setSrcDirs(listOf("test/mods/hooktest/resources"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-nowarn", "-proc:none"))
    // What javac writes without -g: file names and line numbers.
    options.debugOptions.debugLevel = "source,lines"
    dependsOn(bridgeReady)
}

val work = layout.buildDirectory.dir("work")
fun JavaExec.tool(main: String, cp: FileCollection) {
    classpath = cp
    mainClass = main
    dependsOn(bridgeReady)
}

// ---- mods: the Loader itself and the test mod go through the same steps ----

// The game jar with the members opened up, to compile the mods the Loader adapts (the SDK's OpenJar): a mod reads a
// private field without reflection, and ModPatch wires up the access in the game.
val openGame = tasks.register<JavaExec>("openGame") {
    description = "The readable-name game jar with its members opened, for compiling mods."
    val out = work.map { it.file("mc-named-open.jar") }
    inputs.file(namedGameJar)
    outputs.file(out)
    tool("dev.xavier.stein.sdk.OpenJar", sdkTools)
    argumentProviders.add { listOf(namedGameJar.path, out.get().asFile.path) }
    standardOutput = OutputStream.nullOutputStream()
}

dependencies {
    // The Loader compiles against the opened jar too: it reads what the game keeps closed as if it were public, and
    // loaderAccess binds those accesses in the game (see below).
    compileOnly(files(openGame.map { it.outputs.files.singleFile }))
    compileOnly(files(asmCore))
    compileOnly(gameClasspath())
    "hooktestCompileOnly"(files(openGame.map { it.outputs.files.singleFile }))
    "hooktestCompileOnly"(gameClasspath())
}

/** The batch hook of a GL call: what reads client arrays and buffers restores them; everything else flushes. */
fun hookFor(method: String) =
    if (Regex("Pointer|ClientState|ClientActiveTexture|BindBuffer|BufferData|BufferSubData|DeleteBuffers|GenBuffers|" +
            "DrawArrays|DrawElements|ArrayElement|InterleavedArrays").containsMatchIn(method)) "restore" else "flush"

// The matrix calls go to GLMatrix (patcher/redirects-modgl.tsv) and do not touch the driver.
val matrixCalls = Regex("""^org/lwjgl/opengl/GL11\.(gl(MatrixMode|LoadIdentity|PushMatrix|PopMatrix|Ortho|Rotatef|""" +
    """Scalef|Scaled|Translatef|Translated|MultMatrix):|glGetFloat:\(ILjava/nio/FloatBuffer;\)V)""")

/**
 * The steps of a mod (the Loader is one): NamedBuild tosrg, resources, ModRemap with the list of its GL calls, and the
 * batch rules for those calls. Returns the remap task; its outputs are obf.jar and batch.tsv in build/work/<name>.
 */
fun modSteps(name: String, compile: TaskProvider<JavaCompile>, resources: TaskProvider<ProcessResources>,
             compiledAgainst: Provider<File>, extraClasspath: () -> List<File>): TaskProvider<JavaExec> {
    val dir = work.map { it.dir(name) }
    val toSrg = tasks.register<JavaExec>("${name}ToSrg") {
        description = "Takes $name from MCP's readable names to the SRG names."
        val named = compile.flatMap { it.destinationDirectory }
        val out = dir.map { it.dir("classes") }
        inputs.dir(named)
        inputs.files(resources)
        outputs.dir(out)
        tool("dev.xavier.stein.sdk.NamedBuild", sdkTools)
        argumentProviders.add {
            listOf("tosrg", srgFile.path, mcpCsv.resolve("fields.csv").path, mcpCsv.resolve("methods.csv").path,
                compiledAgainst.get().path, named.get().asFile.path, out.get().asFile.path) +
                extraClasspath().map { it.path }
        }
        standardOutput = OutputStream.nullOutputStream()
        doFirst { out.get().asFile.deleteRecursively() }
        doLast {
            fileOps.copy {
                from(resources)
                into(out)
            }
        }
    }
    return tasks.register<JavaExec>("${name}Remap") {
        description = "Takes $name to the game's obfuscated names, checking every reference to the game."
        val classes = toSrg.map { it.outputs.files.singleFile }
        val obf = dir.map { it.file("obf.jar") }
        val calls = dir.map { it.file("gl-calls.txt") }
        val batch = dir.map { it.file("batch.tsv") }
        inputs.files(classes)
        outputs.files(obf, calls, batch)
        tool("ModRemap", bridgeTools + asm)
        argumentProviders.add {
            listOf(srgFile.path, obf.get().asFile.path, "--calls", "org/lwjgl/opengl/", calls.get().asFile.path,
                "--check", gameJar().path, classes.get().path)
        }
        doLast {
            val rules = calls.get().asFile.readLines()
                .filter { Regex("""^org/lwjgl/opengl/(GL[0-9]+|ARB|EXT)[A-Za-z0-9]*\.""").containsMatchIn(it) }
                .filterNot { matrixCalls.containsMatchIn(it) }
                .map { c ->
                    val owner = c.substringBefore('.')
                    val rest = c.substringAfter('.')
                    val method = rest.substringBefore(':')
                    "$owner\t$method\t${rest.substringAfter(':')}\t${hookFor(method)}\t" +
                        "dev/xavier/stein/bridge/engine/ModelBatch\tbefore\n"
                }
            batch.get().asFile.writeText("# Generated by the build from the GL calls of $name.\n" + rules.joinToString(""))
        }
    }
}

// ---- the Loader ----

val loaderRemap = modSteps("steinloader", tasks.compileJava, tasks.processResources, provider { namedGameJar }) {
    listOf(asmCore.singleFile, work.get().dir("steinloader/classes").asFile)
}
val loaderSrgClasses = work.map { it.dir("steinloader/classes") }


// The Loader goes through the Patcher with the Bridge's game rules and its own batch hooks.
val patchLoader = tasks.register<JavaExec>("patchLoader") {
    description = "Patches the Loader with the game rules (LWJGL 3, matrix stack, batching)."
    val obf = work.map { it.file("steinloader/obf.jar") }
    val batch = work.map { it.file("steinloader/batch.tsv") }
    val out = work.map { it.file("steinloader/patched.jar") }
    inputs.files(loaderRemap)
    // the rules too: a rule added to the Bridge must reach the Loader without a clean build
    inputs.files(bridgeRules("mc", "opt", "modgl"), nameMapping)
    outputs.file(out)
    tool("Patcher", patcherClasses + asm)
    systemProperty("patcher.lenient", "true")
    argumentProviders.add {
        listOf(obf.get().asFile.path, out.get().asFile.path, nameMapping.path) +
            bridgeRules("mc", "opt", "modgl").map { it.path } + batch.get().asFile.path
    }
    standardOutput = OutputStream.nullOutputStream()
}

// The rules the Loader applies to mods (ModPatch), in the order the Patcher receives them above. In the game, ModPatch
// reads them from the Bridge jar (steinbridge/rules); here they are for the build's own ModPatch runs.
val rules = tasks.register<Sync>("rules") {
    description = "Collects the rules the Loader applies to mods."
    from(nameMapping)
    from(bridgeRules("mc", "opt", "modgl"))
    into(work.map { it.dir("rules") })
}

val modPatchClasspath = files(loaderSrgClasses, asmCore, provider { if (gameProperties.isFile) listOf(gameJar()) else emptyList() })

// The Loader is compiled against the opened game jar, like a mod: what the real game keeps closed (a private field,
// a protected method from outside its hierarchy) is written as if it were public. After the Patcher, ModPatch
// --access turns each of those accesses into an invokedynamic bound once by internal.Access, as it does for mods.
val loaderAccess = tasks.register<JavaExec>("loaderAccess") {
    description = "Binds the game members the real game keeps closed (ModPatch --access)."
    val out = work.map { it.file("steinloader/access.jar") }
    inputs.files(patchLoader)
    outputs.file(out)
    tool("dev.xavier.stein.loader.ModPatch", modPatchClasspath)
    argumentProviders.add { listOf("--access", patchLoader.get().outputs.files.singleFile.path, out.get().asFile.path) }
}

// ModPatch (the Loader's Patcher, for mods) must do exactly what the build does to the Loader: the Loader itself,
// passed through ModPatch with the same rules, must come out identical to the Patcher's result plus the access step,
// class by class.
val modPatchCheck = tasks.register<JavaExec>("modPatchCheck") {
    group = "verification"
    description = "Checks that ModPatch and the Patcher produce identical classes."
    val obf = work.map { it.file("steinloader/obf.jar") }
    val out = work.map { it.file("steinloader/modpatch.jar") }
    inputs.files(loaderAccess, rules)
    outputs.file(out)
    tool("dev.xavier.stein.loader.ModPatch", modPatchClasspath)
    argumentProviders.add { listOf(rules.get().destinationDir.path, obf.get().asFile.path, out.get().asFile.path) }
    doLast {
        fun classes(f: File) = ZipFile(f).use { z ->
            z.entries().toList().filter { it.name.endsWith(".class") }
                .associate { it.name to z.getInputStream(it).readBytes() }
        }
        val a = classes(out.get().asFile)
        val b = classes(loaderAccess.get().outputs.files.singleFile)
        val differ = a.keys.filter { !b.containsKey(it) || !a[it].contentEquals(b[it]) }
        check(differ.isEmpty()) {
            "ModPatch and Patcher disagree (the Loader would apply something different to mods than the build " +
                "does):\n  " + differ.joinToString("\n  ")
        }
        logger.lifecycle("ModPatch = Patcher: ${a.size} identical classes")
    }
}

// Inside the Loader: ASM, which ModPatch uses and the game does not ship. ASM goes in whole and under
// its usual name (BSD, licence alongside); the Loader's own licence and notices go in META-INF.
val loaderJar = tasks.register<Jar>("loaderJar") {
    group = "build"
    description = "Builds build/libs/steinloader.jar."
    destinationDirectory = layout.buildDirectory.dir("libs")
    archiveFileName = "steinloader.jar"
    from(loaderAccess.map { zipTree(it.outputs.files.singleFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(asmCore.elements.map { it.map { f -> zipTree(f) } }) { exclude("module-info.class", "META-INF/MANIFEST.MF") }
    from("tools/asm-license.txt") {
        into("META-INF")
        rename { "ASM-LICENSE.txt" }
    }
    from(listOf("LICENSE", "LICENSE-API", "NOTICE")) { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar { enabled = false }

val loaderClasses = tasks.register<Sync>("loaderClasses") {
    description = "The Loader jar as a folder (for the tests and the preflight)."
    from(loaderJar.map { zipTree(it.archiveFile) })
    into(layout.buildDirectory.dir("loader/classes"))
}

// Every detoured call of the patched Loader must exist in the Bridge or in LWJGL 3.
val coverage = tasks.register<JavaExec>("coverage") {
    group = "verification"
    description = "Checks that every LWJGL/Stein reference of the patched Loader is served."
    inputs.files(loaderAccess)
    outputs.upToDateWhen { true }
    tool("CoverageCheck", bridgeTools + asm)
    argumentProviders.add {
        listOf(loaderAccess.get().outputs.files.singleFile.path, bridgeDir.resolve("scan/lwjgl3-index.txt").path,
            bridgeShim.path)
    }
}

// ---- the API jar for mod authors ----

// Only the dev.xavier.stein.loader.api package, compiled with the MCP/SRG names — the same ones a mod compiles with.
// dev.xavier.stein.loader.internal (what the Loader itself calls, and the code ModPatch writes into mods) is left out.
val apiPackage = "dev/xavier/stein/loader/api"
val apiClasses = tasks.register<Sync>("apiClasses") {
    from(loaderSrgClasses) { include("$apiPackage/**") }
    into(layout.buildDirectory.dir("api/classes"))
    dependsOn(loaderRemap)
}
// The API mentions nothing besides itself, the JDK and the game: neither the Bridge nor the rest of the Loader.
val apiReferences = tasks.register<JavaExec>("apiReferences") {
    group = "verification"
    description = "Checks that the API references only itself, the JDK and the game."
    inputs.files(apiClasses)
    outputs.upToDateWhen { true }
    tool("ApiReferences", toolsSet.runtimeClasspath)
    argumentProviders.add { listOf(apiClasses.get().destinationDir.path, apiPackage) }
}
// And it must not break a mod compiled against it without a decision: compared with api/stein-api.txt.
// -Pstein.apiUpdate writes the current list, when the change is intentional.
val apiCheck = tasks.register<JavaExec>("apiCheck") {
    group = "verification"
    description = "Compares the API with api/stein-api.txt."
    inputs.files(apiClasses, "api/stein-api.txt")
    outputs.upToDateWhen { true }
    tool("ApiCheck", toolsSet.runtimeClasspath)
    argumentProviders.add {
        listOf(apiClasses.get().destinationDir.path, file("api/stein-api.txt").path) +
            if (providers.gradleProperty("stein.apiUpdate").isPresent) listOf("--update") else emptyList()
    }
}
val apiJar = tasks.register<Jar>("apiJar") {
    group = "build"
    description = "Builds build/libs/stein-api.jar, the API for mod authors."
    dependsOn(apiReferences)
    destinationDirectory = layout.buildDirectory.dir("libs")
    archiveFileName = "stein-api.jar"
    from(apiClasses)
    // MIT, unlike the rest of the Loader: mods that compile against it may use any licence.
    from("LICENSE-API") { into("META-INF") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val apiSourcesJar = tasks.register<Jar>("apiSourcesJar") {
    group = "build"
    description = "Builds build/libs/stein-api-sources.jar."
    destinationDirectory = layout.buildDirectory.dir("libs")
    archiveFileName = "stein-api-sources.jar"
    from("src/main/java") { include("$apiPackage/**") }
    from("LICENSE-API") { into("META-INF") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// ---- tests ----

dependencies { testCompileOnly(files(loaderClasses)) }

// Mods' access to the game's closed members (internal.Access), and the "depends" and "optional" of steinmod.json:
// who gets in, who is refused, and the order.
for ((name, main) in listOf("accessTest" to "AccessTest", "dependenciesTest" to "dev.xavier.stein.loader.DependenciesTest")) {
    tasks.register<JavaExec>(name) {
        group = "verification"
        description = "Runs $main."
        classpath = files(loaderClasses) + sourceSets.test.get().output
        mainClass = main
    }
}

// The hook test mod (test/mods): outside the Loader jar, so it never reaches the player. It ships without the Bridge
// rules — the Loader applies them when loading — so the check runs on what it will actually run: the jar passed
// through the same ModPatch.
dependencies { "hooktestCompileOnly"(files(loaderSrgClasses).builtBy(loaderRemap)) }
val hooktestRemap = modSteps("hooktest", tasks.named<JavaCompile>("compileHooktestJava"),
    tasks.named<ProcessResources>("processHooktestResources"), openGame.map { it.outputs.files.singleFile }) {
    listOf(loaderSrgClasses.get().asFile)
}
val hooktestMod = tasks.register<Copy>("hooktestMod") {
    group = "build"
    description = "Builds build/test-mods/HookTest.steinmod."
    from(hooktestRemap.map { it.outputs.files.first { f -> f.name == "obf.jar" } })
    into(layout.buildDirectory.dir("test-mods"))
    rename { "HookTest.steinmod" }
}
val hooktestModPatch = tasks.register<JavaExec>("hooktestModPatch") {
    description = "Passes the test mod through ModPatch, as the Loader does when loading it."
    val obf = work.map { it.file("hooktest/obf.jar") }
    val out = work.map { it.file("hooktest/patched.jar") }
    inputs.files(hooktestRemap, rules)
    outputs.file(out)
    tool("dev.xavier.stein.loader.ModPatch", modPatchClasspath)
    argumentProviders.add { listOf(rules.get().destinationDir.path, obf.get().asFile.path, out.get().asFile.path) }
}
val hooktestCoverage = tasks.register<JavaExec>("hooktestCoverage") {
    group = "verification"
    description = "Checks the test mod after ModPatch: every LWJGL/Stein reference is served."
    inputs.files(hooktestModPatch, loaderClasses)
    outputs.upToDateWhen { true }
    tool("CoverageCheck", bridgeTools + asm)
    argumentProviders.add {
        listOf(hooktestModPatch.get().outputs.files.singleFile.path, bridgeDir.resolve("scan/lwjgl3-index.txt").path,
            bridgeShim.path, loaderClasses.get().destinationDir.path)
    }
}

// The static initializers of the Loader and the Bridge look up game members: each class is initialized against the
// patched game. On a machine without OpenGL (a CI runner) the check is skipped.
val preflight = tasks.register<JavaExec>("preflight") {
    group = "verification"
    description = "Initializes every Bridge and Loader class against the patched game."
    inputs.files(loaderJar)
    outputs.upToDateWhen { true }
    tool("Preflight", files(loaderJar) + files(bridgeDir.resolve("build/classes/java/test")) + gameClasspath())
    argumentProviders.add { listOf(bridgeShim.path, loaderClasses.get().destinationDir.path) }
    dependsOn(loaderClasses)
}

tasks.test { failOnNoDiscoveredTests = false }
tasks.assemble { dependsOn(loaderJar, apiJar, apiSourcesJar, hooktestMod) }
tasks.check {
    dependsOn(modPatchCheck, coverage, apiReferences, apiCheck, "accessTest", "dependenciesTest", hooktestCoverage,
        preflight)
}

// ---- running the game ----

/** What the game runs with: the Bridge build's game.properties. */
fun runGame(key: String): String =
    Properties().apply { gameProperties.reader().use { load(it) } }.getProperty(key)

/** The Loader jar the game runs with. */
fun runLoaderJar(): File = loaderJar.get().archiveFile.get().asFile

/** What runClient and verify build first. */
val runReady: List<Any> = listOf(loaderJar, bridgeReady)

/** A stream that writes to two others: the console and the run log. */
class Tee(private val a: OutputStream, private val b: OutputStream) : OutputStream() {
    override fun write(c: Int) { a.write(c); b.write(c) }
    override fun write(buf: ByteArray, off: Int, len: Int) { a.write(buf, off, len); b.write(buf, off, len) }
    override fun flush() { a.flush(); b.flush() }
}

/** The java command that plays the game with this Loader: the same JVM settings the installer gives the player. */
fun gameCommand(gameDir: File, jvmArgs: List<String>, gameArgs: List<String> = emptyList()): List<String> {
    val java = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }.get()
    val mem = providers.gradleProperty("stein.mem").getOrElse("2")
    return listOf(java.executablePath.asFile.path, "-Xmx${mem}G", "-Xms256M", "-XX:SoftMaxHeapSize=1024M",
        "-XX:+UseZGC", "-XX:-ZProactive", "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=${runGame("natives")}") + jvmArgs +
        listOf("-cp", runGame("classpath") + File.pathSeparator + runLoaderJar().path,
            "dev.xavier.stein.bridge.launch.Main", "--username", providers.gradleProperty("stein.user")
                .getOrElse("SteinBridge"), "--version", "1.8.9", "--gameDir", gameDir.path,
            "--assetsDir", runGame("assets"), "--assetIndex", "1.8", "--uuid", "00000000000000000000000000000000",
            "--accessToken", "0", "--userProperties", "{}", "--userType", "legacy") + gameArgs
}

/** Puts the mods of -Pstein.mods (a folder of .steinmod files) into the game's mods folder. */
fun installMods(gameDir: File) {
    val mods = providers.gradleProperty("stein.mods").map { file(it) }.orNull ?: return
    mods.listFiles { f -> f.name.endsWith(".steinmod") }?.forEach { it.copyTo(gameDir.resolve("mods/${it.name}"), true) }
}

/**
 * Plays the game through the Stein Bridge with this Loader, in an isolated game folder (build/gamedir). Options (-P):
 * stein.mods (a folder of .steinmod files), stein.jvmArgs, stein.gameDir, stein.runLog, stein.user, stein.mem, and
 * stein.args (more arguments for the game, e.g. "--server 127.0.0.1 --port 25566" for the bench server).
 */
tasks.register("runClient") {
    group = "application"
    description = "Plays the game with this Loader."
    dependsOn(runReady)
    doLast {
        val gameDir = file(providers.gradleProperty("stein.gameDir").getOrElse("build/gamedir")).apply { mkdirs() }
        val runLog = file(providers.gradleProperty("stein.runLog").getOrElse("build/run.log"))
        installMods(gameDir)
        val extra = providers.gradleProperty("stein.jvmArgs").getOrElse("").split(" ").filter(String::isNotBlank)
        val args = providers.gradleProperty("stein.args").getOrElse("").split(" ").filter(String::isNotBlank)
        val p = ProcessBuilder(gameCommand(gameDir, extra, args)).directory(gameDir).redirectErrorStream(true).start()
        runLog.outputStream().use { log ->
            val out = Tee(System.out, log)
            p.inputStream.copyTo(out)
        }
        check(p.waitFor() == 0) { "the game exited with ${p.exitValue()}" }
    }
}

// ---- verification in game ----

/**
 * Plays a singleplayer world (-Pstein.world=<folder in build/gamedir/saves>) with every verification mode on, in both
 * pipelines and both graphics APIs, and fails on any divergence or exception. Run it after changes to rendering, the
 * engine or a patcher rule.
 * Each run opens the world after 8 s and walks forward for a while (the window stays minimized), in third person so the
 * player's own model is checked too. Every check compares Stein's result with the game's own computation.
 *   gfx    Stein GFX with every engine check and every Stein GFX check, about 2 minutes
 *   gpu    the pose computed on the GPU against the CPU one (the GPU pose is off while the CPU pose check runs)
 *   vulkan Stein GFX on the Vulkan backend, with the checks that do not read the OpenGL driver
 *   fixed  the game's fixed-function pipeline (-Dsteinbridge.gfx=false, the fallback) with the engine checks,
 *          including the ones of that pipeline only (Matrix, HudBatch)
 * Logs: build/verify-gfx.log, build/verify-gpu.log, build/verify-vulkan.log, build/verify-fixed.log.
 */
tasks.register("verify") {
    group = "verification"
    description = "Plays a world with every verification mode on, in both pipelines (-Pstein.world=<world>)."
    dependsOn(runReady)
    doLast {
        val world = providers.gradleProperty("stein.world").orNull
            ?: throw GradleException("pass the world: -Pstein.world=<folder in build/gamedir/saves>")
        val gameDir = file("build/gamedir")
        check(gameDir.resolve("saves/$world").isDirectory) { "no world at ${gameDir.resolve("saves/$world")}" }
        // A minimized window loses focus; without this the game pauses and the server stops ticking mid-run.
        val options = gameDir.resolve("options.txt").apply { if (!exists()) createNewFile() }
        var lines = options.readLines()
        for ((k, v) in listOf("pauseOnLostFocus" to "false", "soundCategory_master" to "0.0")) {
            lines = if (lines.any { it.startsWith("$k:") }) lines.map { if (it.startsWith("$k:")) "$k:$v" else it }
            else lines + "$k:$v"
        }
        options.writeText(lines.joinToString("\n", postfix = "\n"))

        val scene = listOf("-Dsteinbridge.diag.background=true", "-Dsteinbridge.diag.singleplayer=$world",
            "-Dsteinbridge.diag.singleplayerAtSec=8", "-Dsteinbridge.diag.thirdPerson=1")
        val walk = listOf("-Dsteinbridge.diag.holdKeyAtSec=30", "-Dsteinbridge.diag.holdKey=17",
            "-Dsteinbridge.diag.holdKeyMs=30000")
        val engineChecks = listOf("ActiveChunks", "Bfs", "Clouds", "CraftInit", "Cull", "EnchCache", "EntityFlags",
            "InfoFilter", "ItemModel", "Light", "SignText", "WatchedMirror").map { "-Dsteinbridge.opt.check$it=true" }
        val gfxChecks = listOf("capture", "gpuHeldItem", "gpuLabel", "particle", "passState", "pose", "recordFrame",
            "retainTiles", "segment", "textCache").map { "-Dsteinbridge.gfx.${it}Check=true" }
        val problems = Regex("""Exception|divergences [1-9]|[^0-9.,][1-9][0-9]* (different|differ from|differing)""" +
            """| [1-9][0-9]* failed""")
        // The JVM itself died (a native crash, or LWJGL aborting on a GL call without a context): the log stops there,
        // with no exception and no divergence, so the problem regex alone would pass it.
        val fatal = Regex("""FATAL ERROR|hs_err|A fatal error has been detected""")
        // The check lines each run must produce: without them the run checked nothing.
        val engineLine = "an engine check line" to Regex("""\] check: [0-9]|\] checked [0-9]""")
        val gfxLine = "a Stein GFX check line" to Regex("""^\[gfx\] .*check.*[0-9]""")
        val gpuLine = "the GPU pose check line" to Regex("""GPU pose check""")
        val failed = mutableListOf<String>()

        fun run(name: String, seconds: Long, jvm: List<String>, bad: Regex,
                expected: List<Pair<String, Regex>>): String {
            val log = layout.buildDirectory.file("verify-$name.log").get().asFile
            logger.lifecycle("== $name (about ${seconds / 60 + 1} minutes)")
            val p = ProcessBuilder(gameCommand(gameDir, scene + jvm)).directory(gameDir).redirectErrorStream(true)
                .redirectOutput(log).start()
            // The game runs until the timeout ends it: exiting earlier with an error code is a crash.
            val exitedEarly = p.waitFor(seconds, TimeUnit.SECONDS)
            if (!exitedEarly) {
                p.descendants().forEach { it.destroyForcibly() }
                p.destroyForcibly().waitFor()
            }
            val text = log.readText()
            val lines = text.lines()
            lines.filter { Regex("""\] (check|.* check)""").containsMatchIn(it) && it.any(Char::isDigit) }
                .map { "   " + it.replace(Regex("[0-9]+"), "N") }.distinct().sorted().take(30).forEach { logger.lifecycle(it) }
            val found = lines.filter { bad.containsMatchIn(it) || fatal.containsMatchIn(it) }
            val reasons = mutableListOf<String>()
            if (exitedEarly && p.exitValue() != 0) {
                reasons += "the game exited with ${p.exitValue()} before the timeout"
            }
            for ((what, line) in expected) {
                if (lines.none { line.containsMatchIn(it) }) reasons += "no $what in ${log.name}"
            }
            if (found.isEmpty() && reasons.isEmpty()) {
                logger.lifecycle("ok: $name — no divergence, no exception")
            } else {
                logger.lifecycle("FAILED: $name")
                reasons.forEach { logger.lifecycle("   $it") }
                found.groupingBy { it }.eachCount().entries.take(20).forEach { logger.lifecycle("   ${it.value} ${it.key}") }
                failed += name
            }
            return text
        }

        run("gfx", 110, walk + engineChecks + gfxChecks, problems, listOf(engineLine, gfxLine))
        // The GPU and the CPU may round the right arm's last bit differently (a known 1-ulp difference); what fails is
        // a wrong command or a wrong rest pose.
        run("gpu", 75, listOf("-Dsteinbridge.diag.holdKeyAtSec=25", "-Dsteinbridge.diag.holdKey=17",
            "-Dsteinbridge.diag.holdKeyMs=20000", "-Dsteinbridge.gfx.gpuPoseCheck=true"),
            Regex("Exception|wrong commands [1-9]|wrong rest [1-9]"), listOf(gpuLine))
        // The same game with the Vulkan backend: the checks that do not read the OpenGL driver (the recorded frame and
        // the pass state are the OpenGL backend's), and no hidden OpenGL context, so a call to OpenGL that slipped
        // through aborts the run instead of landing on a context nobody sees.
        val vulkanChecks = gfxChecks.filterNot { it.contains("recordFrame") || it.contains("passState") }
        run("vulkan", 110, walk + "-Dsteinbridge.gfx.api=vulkan" + "-Dsteinbridge.gfx.vkGlSink=false" +
            engineChecks + vulkanChecks, problems, listOf(engineLine, gfxLine))
        run("fixed", 110, walk + "-Dsteinbridge.gfx=false" + engineChecks + "-Dsteinbridge.opt.checkMatrix=true" +
            "-Dsteinbridge.opt.checkHudBatch=true", problems, listOf(engineLine))
        if (failed.isNotEmpty()) throw GradleException("verification failed: ${failed.distinct().joinToString()}")
    }
}

// ---- API documentation ----

// The mod API's Javadoc with doclint on (-Xdoclint:all): bad HTML, a broken {@link} or a public member of the api
// package without a comment fails the build. Not required: an @param/@return/@throws on every member (most of the
// API documents them in the sentence), nor comments on api.spi (the Loader implements it; mods never call it).
// Only the api package, against the game (it references nothing else). Output: build/docs/api.
val apiJavadoc = tasks.register("apiJavadoc") {
    group = "verification"
    description = "Builds the mod API's Javadoc with doclint and fails on any problem."
    val sources = fileTree("src/main/java") { include("$apiPackage/**/*.java") }
    val classpath = files(namedGameJar) + gameClasspath()
    val out = layout.buildDirectory.dir("docs/api")
    val argFile = layout.buildDirectory.file("tmp/apiJavadoc/args.txt")
    val javadoc = javaToolchains.javadocToolFor { languageVersion = JavaLanguageVersion.of(25) }
    inputs.files(sources)
    outputs.dir(out)
    dependsOn(bridgeReady)
    doLast {
        val dir = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        // An argument file (the command line would be too long on Windows), with forward slashes: javadoc reads a
        // backslash in a quoted argument as an escape.
        fun quoted(path: String) = "\"" + path.replace(File.separatorChar, '/') + "\""
        val args = listOf("-quiet", "-encoding", "UTF-8", "-notimestamp", "-Xdoclint:all", "-Xmaxwarns", "100000",
            "-Xmaxerrs", "100000", "-d", quoted(dir.path), "-cp", quoted(classpath.asPath)) +
            sources.files.sorted().map { quoted(it.path) }
        val file = argFile.get().asFile.apply { parentFile.mkdirs(); writeText(args.joinToString("\n")) }
        val p = ProcessBuilder(javadoc.get().executablePath.asFile.path, "@" + file.path).redirectErrorStream(true)
            .start()
        val text = p.inputStream.bufferedReader().readText()
        val exit = p.waitFor()
        val problems = text.lines().filter { Regex("""(warning|error):""").containsMatchIn(it) }
            .filterNot { Regex("""warning: no @(param|return|throws)\b""").containsMatchIn(it) }
            .filterNot { Regex("""[\\/]api[\\/]spi[\\/][^:]*:\d+: warning: no comment""").containsMatchIn(it) }
        if (problems.isNotEmpty() || exit != 0) {
            throw GradleException("the API Javadoc has problems:\n  " +
                problems.ifEmpty { text.lines().filter(String::isNotBlank).take(20) }.joinToString("\n  "))
        }
        logger.lifecycle("API Javadoc: clean (build/docs/api)")
    }
}
tasks.check { dependsOn(apiJavadoc) }

// ---- the -D options ----

// docs/OPTIONS.md lists every -D option of the Loader, from the Javadoc of the *Options classes (tools/Options.java).
for ((name, check) in listOf("options" to false, "optionsCheck" to true)) {
    tasks.register<JavaExec>(name) {
        group = if (check) "verification" else "documentation"
        description = if (check) "Checks that docs/OPTIONS.md is up to date." else "Writes docs/OPTIONS.md."
        classpath = toolsSet.runtimeClasspath
        mainClass = "Options"
        args(listOf(file("src/main/java").path, file("docs/OPTIONS.md").path) + if (check) listOf("--check") else emptyList())
        if (check) {
            inputs.files(fileTree("src/main/java") { include("**/*Options.java") }, "docs/OPTIONS.md")
            outputs.upToDateWhen { true }
        }
    }
}
tasks.check { dependsOn("optionsCheck") }

// ---- formatting ----

spotless {
    // LF everywhere, as committed (.editorconfig); Windows checkouts too.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
        target("src/**/*.java", "test/**/*.java", "tools/*.java")
        googleJavaFormat(libs.versions.google.java.format.get()).aosp().reorderImports(true)
    }
}
