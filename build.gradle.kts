plugins {
    java
}

group = "divinejason"
version = "1.0.0"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    // TODO: add compileOnly dependencies for ItemsAdder / IceStorm when the exact
    // API artifacts used by the server are confirmed.
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Paper only provides the Paper API and plugin dependencies such as Vault.
// SQLite JDBC and HikariCP are normal JVM libraries, so the plugin jar must
// include them. Without this fat-jar step, the server can compile the plugin
// but fail at startup with NoClassDefFoundError for HikariConfig or org.sqlite.JDBC.
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations.runtimeClasspath.get()
            .filter { dependency -> dependency.name.endsWith(".jar") }
            .map { dependency -> zipTree(dependency) }
    })

    // Maven artifacts can contain signature metadata that becomes invalid once
    // their classes are unpacked into this plugin jar.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
