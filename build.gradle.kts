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
    // Fabric
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    
    val mcVersion = properties["minecraft_version"] as String
    if (mcVersion.startsWith("26")) {
        mappings(loom.officialMojangMappings())
    } else {
        mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    }
    
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Meteor
    modImplementation("meteordevelopment:meteor-client:${properties["minecraft_version"] as String}-SNAPSHOT")
}

tasks {
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