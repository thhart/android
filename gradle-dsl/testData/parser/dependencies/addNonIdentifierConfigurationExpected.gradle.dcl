androidApp {
  buildTypes {
    buildType("dotted.buildtype") {
    }
  }

  dependenciesDcl {
    implementation("com.android.support:appcompat-v7:+")
  `dotted.buildtypeImplementation`("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.1")
  }
}
