import org.gradle.api.tasks.Copy

plugins {
    kotlin("jvm") version "2.1.21"
    application
}

group = "com.l2kt"
version = "1.0.0"

repositories {
    mavenCentral()
}

// ─────────────────────────────────────────────────────────
// Dependencies (modernized: HikariCP replacing C3P0,
// MySQL 8.0+ replacing 5.1.26, coroutines latest stable)
// ─────────────────────────────────────────────────────────
dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Database
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging (SLF4J para HikariCP)
    implementation("org.slf4j:slf4j-jdk14:2.0.12")
}

// ─────────────────────────────────────────────────────────
// Source Sets: mapeia a estrutura legada src/ (não src/main/kotlin)
// ─────────────────────────────────────────────────────────
sourceSets {
    main {
        kotlin.srcDirs("src")
        java.srcDirs("src")
        resources.srcDirs("config")
    }
}

// ─────────────────────────────────────────────────────────
// Java toolchain — use whatever JDK is available (17+)
// ─────────────────────────────────────────────────────────
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// ─────────────────────────────────────────────────────────
// Kotlin compiler options
// ─────────────────────────────────────────────────────────
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(false)  // Deprecations are warnings, not errors
    }
}
// ─────────────────────────────────────────────────────────
tasks.jar {
    archiveBaseName.set("l2kt")
    manifest {
        attributes(
            "Main-Class" to "com.l2kt.Server",
            "Class-Path" to configurations.runtimeClasspath.get()
                .files.joinToString(" ") { it.name }
        )
    }
}

// ─────────────────────────────────────────────────────────
// DISTRIBUTION TASKS
// Replica o comportamento do Ant: build/dist/{login,gameserver}
// ─────────────────────────────────────────────────────────

val distDir = layout.buildDirectory.dir("dist")
val distLogin = layout.buildDirectory.dir("dist/login")
val distGame = layout.buildDirectory.dir("dist/gameserver")

// Task: Copia libs (JAR compilado + dependencies) para ambos os servers
val copyLibsLogin by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from(configurations.runtimeClasspath)
    into(distLogin.map { it.dir("libs") })
}

val copyLibsGame by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    from(configurations.runtimeClasspath)
    into(distGame.map { it.dir("libs") })
}

// Task: Copia config para cada server
val copyConfigLogin by tasks.registering(Copy::class) {
    from("config") {
        include("banned_ips.properties")
        include("logging.properties")
        include("loginserver.properties")
        include("hexid.txt")
    }
    into(distLogin.map { it.dir("config") })
}

val copyConfigGame by tasks.registering(Copy::class) {
    from("config") {
        include("*.properties")
        include("*.txt")
    }
    into(distGame.map { it.dir("config") })
}

// Task: Copia data/ (XMLs + HTMLs) para GameServer
val copyDataGame by tasks.registering(Copy::class) {
    from("data")
    into(distGame.map { it.dir("data") })
}

// Task: Copia scripts de launch
val copyScriptsLogin by tasks.registering(Copy::class) {
    from("dist") {
        include("startAccountManager.*")
        include("startSQLAccountManager.*")
        include("LoginServer_loop.sh")
        include("startLoginServer.*")
        include("RegisterGameServer.*")
    }
    into(distLogin)
}

val copyScriptsGame by tasks.registering(Copy::class) {
    from("dist") {
        include("GameServer_loop.sh")
        include("startGameServer.*")
        include("GeoDataConverter.*")
    }
    into(distGame)
}

// Task: Copia SQL para distribuição
val copySql by tasks.registering(Copy::class) {
    from("sql")
    into(distDir.map { it.dir("sql") })
}

val copyTools by tasks.registering(Copy::class) {
    from("tools")
    into(distDir.map { it.dir("tools") })
}

// Task: Cria diretórios de log
val createDirs by tasks.registering {
    doLast {
        distLogin.get().dir("log").asFile.mkdirs()
        distGame.get().dir("log").asFile.mkdirs()
    }
}

// ─────────────────────────────────────────────────────────
// Master distribution task: `./gradlew dist`
// ─────────────────────────────────────────────────────────
val dist by tasks.registering {
    group = "distribution"
    description = "Builds the full server distribution (login + gameserver)"
    dependsOn(
        copyLibsLogin, copyLibsGame,
        copyConfigLogin, copyConfigGame,
        copyDataGame,
        copyScriptsLogin, copyScriptsGame,
        copySql, copyTools,
        createDirs
    )
    doLast {
        println("═══════════════════════════════════════════")
        println("  Distribution built: build/dist/")
        println("  ├── login/       (LoginServer)")
        println("  ├── gameserver/  (GameServer)")
        println("  ├── sql/         (Database schemas)")
        println("  └── tools/       (Utilities)")
        println("═══════════════════════════════════════════")
    }
}

// Fix: ensure shell scripts are executable
tasks.register("fixPermissions") {
    dependsOn(dist)
    doLast {
        fileTree(distDir) {
            include("**/*.sh")
        }.forEach { it.setExecutable(true) }
    }
}
