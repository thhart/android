buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.0")
        classpath("androidx.benchmark:benchmark-baseline-profile-gradle-plugin:1.2.0")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
