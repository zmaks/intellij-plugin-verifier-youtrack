plugins {
  war
  idea
  `maven-publish`
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependencyManagement)
  alias(sharedLibs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring) version sharedLibs.versions.kotlin
}

kotlin {
  jvmToolchain(11)
}

tasks {
  compileKotlin {
    kotlinOptions {
      apiVersion = "1.4"
      languageVersion = "1.4"
    }
  }
  publishToMavenLocal {
    dependsOn(test)
  }
}

val serviceVersion = project.properties
        .getOrDefault("verifierServiceProjectVersion", "1.0").toString()

allprojects {
  version = serviceVersion
  group = "org.jetbrains.intellij.plugins.verifier"

  idea {
    module {
      inheritOutputDirs = false
      outputDir = File("$buildDir/classes/main")
    }
  }

  tasks {
    bootRun {
      @Suppress("UNCHECKED_CAST")
      systemProperties = System.getProperties().toMap() as Map<String, Any>
    }
    springBoot {
      buildInfo()
    }
  }

  repositories {
    mavenLocal()
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://packages.jetbrains.team/maven/p/teamcity-rest-client/teamcity-rest-client")
  }

  configurations {
    developmentOnly
    runtimeClasspath {
      extendsFrom(developmentOnly.get())
    }
  }

  dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.tomcat)
    implementation(libs.spring.boot.devtools)

    testImplementation(libs.junit)
    implementation(sharedLibs.kotlin.stdlib.jdk8)
    implementation(libs.commons.io)
    implementation(libs.kotson)
    implementation(libs.gson)

    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging.interceptor)

    //Simple map-database engine that allows to store maps on disk: https://github.com/jankotek/mapdb/
    implementation(libs.mapdb)
    implementation(sharedLibs.slf4j.api)
    implementation(libs.logback.classic)

    runtimeOnly(libs.groovy)
    implementation(libs.commons.fileupload)
    implementation("org.jetbrains.intellij.plugins:intellij-feature-extractor:dev")
    implementation("org.jetbrains.intellij.plugins:verifier-intellij:dev")

    implementation(libs.teamcity.restClient)
  }
}