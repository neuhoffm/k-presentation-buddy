// Copyright (c) 2025 Advntrs LLC
// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "k-presentation-buddy"
        version = providers.gradleProperty("pluginVersion").get()

        ideaVersion {
            sinceBuild = "241"
        }

        vendor {
            name = "Advntrs LLC"
            url = "https://github.com/neuhoffm/k-presentation-buddy"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(providers.gradleProperty("jvmTarget").get())
    }
}

java {
    val jvmTargetStr = providers.gradleProperty("jvmTarget").get()
    sourceCompatibility = JavaVersion.toVersion(jvmTargetStr)
    targetCompatibility = JavaVersion.toVersion(jvmTargetStr)
}
