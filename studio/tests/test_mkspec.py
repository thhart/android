import os
import unittest
import subprocess
import sys
import tempfile
from tools.adt.idea.studio.tests import test_utils

class MkSpecTest(unittest.TestCase):

  def _run(self, script, path, out):
    # A cross-platform way of running a python script.
    ret = subprocess.run(
        [sys.executable, script, "--path", path, "--out", out],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env = {"PYTHONPATH": os.path.dirname(script)}
    )
    return ret

  def test_mkspec(self):
    deploy_dir = test_utils.deploy_py("mkspec_files")

    download = tempfile.mkdtemp()
    test_utils.create_all(download, {"lib/app.jar": {"__index__": "data"},
        "lib/resources.jar": {
            "idea/WhiteSpacerApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "lib/modules/intellij.platform.coverage.jar": {},
        "lib/testFramework.jar": {},
        "plugins/foo/lib/foo.jar": {"META-INF/plugin.xml": "<idea-plugin><id>com.sample.foo</id></idea-plugin>"},
        "plugins/bar/lib/bar.jar": {
          "META-INF/plugin.xml": """<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
             <xi:include href="/META-INF/Other.xml" xpointer="xpointer(/idea-plugin/*)" />
           </idea-plugin>""",
          "META-INF/Other.xml": """<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
             <id>com.sample.bar</id>
           </idea-plugin>"""},
        "plugins/common/lib/common.jar": {"META-INF/plugin.xml": "<idea-plugin><id>com.sample.common</id></idea-plugin>"},
        "plugins/common/lib/modules/common.plugin.module.jar": {"common.plugin.module.xml": "<idea-plugin></idea-plugin>"},
        "plugins/noid/lib/noid.jar": {"META-INF/plugin.xml": "<idea-plugin><name>the_name</name></idea-plugin>"},
        "product-info.json": {
            "launch": [{
                "bootClassPathJarNames": ["util.jar"],
                "additionalJvmArguments": [
                  "-Didea.platform.prefix=WhiteSpacer",
                  "--add-opens=java.base/java.io=ALL-UNNAMED",
                  "--add-opens=java.base/java.net=ALL-UNNAMED",
                  "--add-exports=java.base/java.lang=ALL-UNNAMED",
                ],
            }]
        },
    })

    bzl = os.path.join(download, "spec.bzl")
    ret = self._run(deploy_dir + os.path.normpath("/tools/adt/idea/studio/mkspec.py"), download, bzl)
    ret.check_returncode()


    expected = """# Auto-generated file, do not edit manually.
SPEC = struct(
    major_version = "2024",
    minor_version = "1",
    jars = [
        "/lib/util.jar",
    ],
    jars_linux = [
    ],
    plugin_jars = {
        "com.sample.bar": [
            "plugins/bar/lib/bar.jar",
        ],
        "com.sample.common": [
            "plugins/common/lib/common.jar",
        ],
        "com.sample.foo": [
            "plugins/foo/lib/foo.jar",
        ],
        "common.plugin.module": [
            "plugins/common/lib/modules/common.plugin.module.jar",
        ],
        "intellij.platform.coverage": [
            "lib/modules/intellij.platform.coverage.jar",
        ],
        "the_name": [
            "plugins/noid/lib/noid.jar",
        ],
    },
    plugin_jars_linux = {
    },
    mac_bundle_name = "",
    add_exports = [
        "java.base/java.lang",
    ],
    add_opens = [
        "java.base/java.io",
        "java.base/java.net",
    ],
)
"""
    actual = test_utils.readstr(bzl)
    if expected != actual:
      print(actual)
    self.assertEqual(expected, actual)

  def test_no_prefix(self):
    deploy_dir = test_utils.deploy_py("mkspec_files")

    download = tempfile.mkdtemp()
    test_utils.create_all(download, {"lib/app.jar": {"__index__": "data"},
        "lib/any.jar": {
            "idea/ApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "product-info.json": {
            "launch": [{
                "bootClassPathJarNames": [],
                "additionalJvmArguments": [],
            }]
        },
    })

    bzl = os.path.join(download, "spec.bzl")
    ret = self._run(deploy_dir + os.path.normpath("/tools/adt/idea/studio/mkspec.py"), download, bzl)
    ret.check_returncode()


    expected = """# Auto-generated file, do not edit manually.
SPEC = struct(
    major_version = "2024",
    minor_version = "1",
    jars = [
    ],
    jars_linux = [
    ],
    plugin_jars = {
    },
    plugin_jars_linux = {
    },
    mac_bundle_name = "",
    add_exports = [
    ],
    add_opens = [
    ],
)
"""
    actual = test_utils.readstr(bzl)
    if expected != actual:
      print(actual)
    self.assertEqual(expected, actual)

if __name__ == "__main__":
  unittest.main()