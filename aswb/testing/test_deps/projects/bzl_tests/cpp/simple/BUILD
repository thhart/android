load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_cc//cc:cc_library.bzl", "cc_library")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

package(
    default_visibility = ["//visibility:public"],
)

cc_library(
    name = "simple_lib",
    srcs = ["simple/simple.cc"],
    hdrs = ["simple/simple.h"],
    copts = [
        "-DGOPT",
        "-Ifoo/baz/",
        "-I",
        "other/headers",
    ],
    defines = ["VERSION2"],
    includes = ["foo/bar"],
    textual_hdrs = ["simple/simple_textual.h"],
)

cc_binary(
    name = "simple",
    srcs = ["simple/simple.cc"],
)

build_dependencies_deps_test_fixture(
    name = "cc_library_test_fixture",
    deps = [":simple_lib"],
)

build_dependencies_deps_test_fixture(
    name = "cc_binary_test_fixture",
    deps = [":simple"],
)

test_suite(name = "cpp")
