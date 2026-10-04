// AGP 9 hat "Built-in Kotlin": Das Plugin org.jetbrains.kotlin.android darf NICHT
// mehr angewendet werden. Wir legen die Kotlin-Version stattdessen auf den
// Buildscript-Classpath, um sie festzulegen (AGP 9.4 bringt sonst eine
// fest verdrahtete KGP-Version mit).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}