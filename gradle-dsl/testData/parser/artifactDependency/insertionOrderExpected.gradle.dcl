androidApp {
  dependenciesDcl {
    feature("com.example.libs3:lib3:3.0")
    api("com.example.libs1:lib1:1.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    testCompile("com.example.libs2:lib2:2.0")
  }
}
