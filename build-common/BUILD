load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.buildCommon",
    srcs = ["src"],
    iml_files = ["intellij.android.buildCommon.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/device_validator:studio.android.sdktools.dvlib",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "@intellij//:com.intellij.gradle",
    ],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/device_validator:studio.android.sdktools.dvlib[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "@intellij//:com.intellij.gradle",
    ],
)
