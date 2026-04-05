plugins {
    id("fabric-loom") version "1.16.1"
    id("com.diffplug.spotless") version "8.0.0"
}

base {
    archivesName = properties["archives_base_name"] as String
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    val mcVersion = properties["minecraft_version"] as String
    val isUnobfuscated = mcVersion.contains("26")
    val loaderVersion = if (project.hasProperty("loader_version")) properties["loader_version"] as String else "0.17.3"

    // Minecraft
    minecraft("com.mojang:minecraft:$mcVersion")
    
    if (!isUnobfuscated) {
        mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    }
    
    // Loader
    if (isUnobfuscated) {
        implementation("net.fabricmc:fabric-loader:$loaderVersion")
    } else {
        modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    }

    // Meteor
    if (isUnobfuscated) {
        implementation("meteordevelopment:meteor-client:${mcVersion}-SNAPSHOT")
    } else {
        modImplementation("meteordevelopment:meteor-client:${mcVersion}-SNAPSHOT")
    }
}

tasks {
    val mcVersion = project.property("minecraft_version") as String
    val isUnobfuscated = mcVersion.contains("26")

    if (isUnobfuscated) {
        // Disable remapping for unobfuscated versions
        named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
            enabled = false
        }
        named<net.fabricmc.loom.task.RemapSourcesJarTask>("remapSourcesJar") {
            enabled = false
        }
    }
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version"),
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        val javaVersion = if (project.hasProperty("java_version")) JavaVersion.toVersion(project.property("java_version")!!) else JavaVersion.VERSION_21
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        val javaRelease = if (project.hasProperty("java_version")) (project.property("java_version") as String).toInt() else 21
        options.release.set(javaRelease)
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

}
java {
    toolchain {
        val javaVersion = if (project.hasProperty("java_version")) JavaLanguageVersion.of(project.property("java_version") as String) else JavaLanguageVersion.of(21)
        languageVersion.set(javaVersion)
    }
}