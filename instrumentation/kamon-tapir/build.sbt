lazy val Legacy = config("legacy")
lazy val TestLegacy = config("test-legacy")

configs(Legacy, TestLegacy)
inConfig(Legacy)(Defaults.compileSettings)
inConfig(TestLegacy)(Defaults.testSettings)

Compile / products := (Compile / products).value ++ (Legacy / products).value

Test / test := {
  (Test / test).value
  (TestLegacy / test).value
}