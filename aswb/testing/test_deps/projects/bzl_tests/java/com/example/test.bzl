"""Rules for testing third_party/intellij/bazel/plugin/aspect/build_dependencies_blaze_deps.bzl with java target."""

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", _test_suite = "test_suite")
load("//bzl_tests:check_utils.bzl", "label_info_factory")
load("//bzl_tests:test_fixture.bzl", "TargetInfo")
load(":check_utils.bzl", "java_info_factory")

JAVA_LIBRARY_TARGET = "foolib"
JAVA_BINARY_TARGET = "foo"
JAVA_LIBRARY_DEPS_TARGET = "distant"
TEST_TARGET_PACKAGE = "bzl_tests/java/com/example"

def _java_library_test(name, **test_kwargs):
    analysis_test(name = name, impl = _java_library_test_impl, target = ":java_library_test_fixture", **test_kwargs)

def _java_library_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            java_info = java_info_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, JAVA_LIBRARY_TARGET))
    actual.java_info().contains_exactly(
        struct(
            compile_jars_depset = ["*.jar"],
            generated_outputs = [struct(
                generated_source_jar = "",
                generated_class_jar = "",
            )],
            java_output_compile_jars = ["*.jar"],
            transitive_compile_time_jars_depset = ["*.jar", "*.jar"],
            transitive_runtime_jars_depset = ["*.jar", "*.jar"],
        ),
    )

def _java_binary_test(name, **test_kwargs):
    analysis_test(name = name, impl = _java_binary_test_impl, target = ":java_binary_test_fixture", **test_kwargs)

def _java_binary_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            java_info = java_info_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, JAVA_BINARY_TARGET))
    actual.java_info().contains_exactly(
        struct(
            compile_jars_depset = [],
            generated_outputs = [struct(
                generated_source_jar = "",
                generated_class_jar = "",
            )],
            java_output_compile_jars = [],
            transitive_compile_time_jars_depset = [],
            transitive_runtime_jars_depset = [],
        ),
    )

def test_suite(name):
    _test_suite(
        name = name,
        tests = [
            _java_library_test,
            _java_binary_test,
        ],
    )
