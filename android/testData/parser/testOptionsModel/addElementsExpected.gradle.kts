android {
  testOptions {
    reportDir = "reportDirectory"
    resultsDir = "resultsDirectory"
    execution = "ANDROID_TEST_ORCHESTRATOR"
    unitTests {
      isReturnDefaultValues = true
    }
  }
}
