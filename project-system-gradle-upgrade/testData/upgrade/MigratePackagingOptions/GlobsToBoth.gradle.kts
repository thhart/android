android {
  packagingOptions {
    pickFirst("foo.{so,jar}")
    pickFirst("bar.*")
    exclude("baz.??")
    exclude("quux.[a-z][a-z]")
  }
}