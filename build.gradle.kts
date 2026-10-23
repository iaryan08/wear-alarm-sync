// Root build.gradle.kts — plugin declarations only; each module applies what it needs.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler)     apply false
    alias(libs.plugins.ksp)                  apply false
}
