androidApp {
  buildToolsVersion = "23.0.0"
  compileSdkVersion = "23"
  defaultPublishConfig = "debug"
  //dynamicFeatures = [":f1", ":f2"]
  //flavorDimensions "abi", "version"
  generatePureSplits = true
  publishNonDefault = false
  resourcePrefix = "abcd"
  targetProjectPath = ":tpp"
}