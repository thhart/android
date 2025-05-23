"""A module containing a representation of an intellij IDE installation."""

from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Set, Dict
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

LINUX = "linux"
WIN = "windows"
MAC = "darwin"
MAC_ARM = "darwin_aarch64"

_idea_home = {
    LINUX: "",
    WIN: "",
    MAC: "Contents",
    MAC_ARM: "Contents",
}

_idea_resources = {
    LINUX: "",
    WIN: "",
    MAC: "Contents/Resources",
    MAC_ARM: "Contents/Resources",
}


@dataclass(frozen=True)
class IntelliJ:
  major: str
  minor: str
  platform: str = ""
  platform_jars: Set[str] = field(default_factory=lambda: set())
  plugin_jars: Dict[str, Set[str]] = field(default_factory=lambda: dict())
  jvm_add_exports: List[str] = field(default_factory=lambda: list())
  jvm_add_opens: List[str] = field(default_factory=lambda: list())

  def version(self):
    return self.major, self.minor

  def create(platform: str, path: Path):
    product_info = read_product_info(
        path/_idea_resources[platform]/"product-info.json"
    )
    prefix = _read_platform_prefix(product_info)
    major, minor = read_version(path/_idea_home[platform]/"lib", prefix)
    jars = read_platform_jars(path/_idea_home[platform], product_info)
    plugin_jars = _read_plugin_jars(path/_idea_home[platform])
    add_exports = _read_jvm_args("--add-exports=","=ALL-UNNAMED", product_info)
    add_opens = _read_jvm_args("--add-opens=","=ALL-UNNAMED", product_info)
    return IntelliJ(major, minor, platform_jars=jars, plugin_jars=plugin_jars, jvm_add_exports=add_exports, jvm_add_opens=add_opens)


def read_product_info(path):
  with open(path) as f:
    return json.load(f)


def read_version(lib_dir: Path, prefix: str) -> (str, str):
  contents = None
  for resources_jar in lib_dir.glob("*.jar"):
    with zipfile.ZipFile(resources_jar) as zip:
      file_name = f"idea/{prefix}ApplicationInfo.xml"
      if file_name in zip.namelist():
        data = zip.read(file_name)
        contents = data.decode("utf-8")
        break
  if not contents:
    sys.exit("Failed to find ApplicationInfo.xml for idea.prefix=" + prefix)
  m = re.search(r'<version.*major="([\d\.]+)".*minor="([\d\.]+)".*>', contents)
  major = m.group(1)
  minor = m.group(2)
  return major, minor


def read_platform_jars(ide_home: Path, product_info):
  # Extract the runtime classpath from product-info.json.
  bootClassPath = product_info["launch"][0]["bootClassPathJarNames"]
  jars = ["/lib/" + jar for jar in bootClassPath]
  return set(jars)


def _read_platform_prefix(product_info):
  launch_config = product_info["launch"][0]
  for define in launch_config["additionalJvmArguments"]:
    m = re.search("-Didea.platform.prefix=(.*)", define)
    if m:
      return m.group(1)
  # IJ ultimate has a platform prefix if "", so it's not added here. If not found assume it's empty.
  return ""


def _read_zip_entry(zip_path, entry):
  with zipfile.ZipFile(zip_path) as zip:
    if entry not in zip.namelist():
      return None
    data = zip.read(entry)
  return data.decode("utf-8")


def _read_plugin_id(path: Path):
  jars = path.glob("lib/*.jar")
  xml = load_plugin_xml(jars)

  # The id of a plugin is defined as the id tag and if missing, the name tag.
  ids = [id.text for id in xml.findall("id")]
  if len(set(ids)) > 1:
    sys.exit(f"Too many plugin ids found in plugin: {path}")
  if len(ids) >= 1:
    return ids[0]
  names = xml.findall("name")
  if len(names) > 1:
    sys.exit(f"Too many plugin names found (for plugin without id): {path}")
  if len(names) == 1:
    return names[0].text
  sys.exit(f"Cannot find plugin id or name tag for plugin: {path}")


def _read_plugin_jars(idea_home: Path):
  plugins = {}
  for plugin_path in idea_home.glob("plugins/*"):
    if not plugin_path.is_dir():
      continue
    plugin_id = _read_plugin_id(plugin_path)
    jars = plugin_path.glob("lib/*.jar")
    jar_paths = ["/" + str(jar.relative_to(idea_home).as_posix()) for jar in jars]
    assert plugin_id not in plugins, f"Duplicated plugin ID: {plugin_id}"
    plugins[plugin_id] = set(jar_paths)
  # We also model V2 modules as plugins---at least for now, until the V2 design solidifies upstream.
  # See b/349849955 and go/studio-v2-modules for details.
  for jar in [*idea_home.glob("lib/modules/*.jar"), *idea_home.glob("plugins/*/lib/modules/*.jar")]:
    module_id = jar.stem
    jar_path = "/" + str(jar.relative_to(idea_home).as_posix())
    assert module_id not in plugins, f"Duplicated plugin ID: {module_id}"
    plugins[module_id] = set([jar_path])
  return plugins


def _load_include(include, xpath, cwd, index):
  href = include.get("href")
  parse = include.get("parse", "xml")
  if parse != "xml":
    print("only xml parse is supported")
    sys.exit(1)
  is_optional = any(
      child.tag == "{http://www.w3.org/2001/XInclude}fallback"
      for child in include
  )
  if is_optional:
    return [], None

  # See `PluginXmlPathResolver.toLoadPath` for the platform implementation
  rel = href
  if rel not in index:
    if href.startswith("/"):
      rel = href[1:]
    elif cwd == "":
      # By default, plugin xmls are resolved from META-INF
      rel = "META-INF/" + href
    else:
      rel = cwd + "/" + href

  new_cwd = rel[0 : rel.rindex("/")] if "/" in rel else ""

  if rel not in index:
    print("Cannot find file to include %s" % href)
    sys.exit(1)

  res = index[rel].read(rel)
  e = ET.fromstring(res)

  ret = []
  assert xpath.startswith("/")
  root, path = xpath[1:].split("/", 1)
  if root == e.tag:
    ret = e.findall("./" + path)
  if not ret:
    print("While including %s, the path %s," % (rel, xpath))
    print("did not produce any elements to include")
    sys.exit(1)

  return ret, new_cwd


def _xpath_for_include(include, parent):
  # The IntelliJ plugin XML reader has custom handling for <xi:include> elements, which we emulate
  # here. For example, it has hard-coded defaults and constraints for the xpointer attribute. See:
  # https://github.com/JetBrains/intellij-community/blob/f57df70730/platform/core-impl/src/com/intellij/ide/plugins/XmlReader.kt#L39
  if parent.tag == "idea-plugin":
    xpath = "/idea-plugin/*"
  elif parent.tag == "extensionPoints":
    xpath = "/idea-plugin/extensionPoints/*"
  else:
    sys.exit(f"<xi:include> is unsupported beneath <{parent.tag}>")
  # Check that the xpointer attribute (if any) is consistent with our inferred xpath.
  xpointer = include.get("xpointer")
  if xpointer != None and xpointer != f"xpointer({xpath})":
    sys.exit(f"<xi:include> has invalid xpointer attribute: {xpointer}")
  return xpath


def _resolve_includes(elem, cwd, index):
  """Resolves xincludes in the given xml element.

  By replacing xinclude tags like

  <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <xi:include href="/META-INF/android-plugin.xml"/>
    ...

  with the xml pointed by href and the xpath given in xpointer.
  """

  i = 0
  while i < len(elem):
    e = elem[i]
    if e.tag == "{http://www.w3.org/2001/XInclude}include":
      xpath = _xpath_for_include(e, elem)
      nodes, new_cwd = _load_include(e, xpath, cwd, index)
      subtree = ET.Element(elem.tag)
      subtree.extend(nodes)
      _resolve_includes(subtree, new_cwd, index)
      nodes = list(subtree)
      if nodes:
        for node in nodes[:-1]:
          elem.insert(i, node)
          i = i + 1
        node = nodes[len(nodes) - 1]
        if e.tail:
          node.tail = (node.tail or "") + e.tail
        elem[i] = node
    else:
      _resolve_includes(e, cwd, index)
    i = i + 1


def load_plugin_xml(files: List[Path], xml_name = "META-INF/plugin.xml"):
  xmls = {}
  index = {}
  jars = [zipfile.ZipFile(f) for f in files if f.suffix == ".jar"]
  for jar in jars:
    for jar_entry in jar.namelist():
      if jar_entry == xml_name:
        xmls[f"{jar.filename}!{jar_entry}"] = jar.read(jar_entry)
      if not jar_entry.endswith("/"):
        # TODO: Investigate if we can have a strict mode where we fail on duplicate
        # files across jars in the same plugin. Currently even IJ plugins fail with
        # such a check as they have even .class files duplicated in the same plugin.
        index[jar_entry] = jar

  if len(xmls) != 1:
    for file in xmls:
      print(f"Found {xml_name} at {file}")
    sys.exit(f"ERROR: plugin should have exactly one file named {xml_name} (found {len(xmls)})")

  _, xml = list(xmls.items())[0]
  element = ET.fromstring(xml)

  # We cannot use ElementInclude because it does not support xpointer
  _resolve_includes(element, "META-INF", index)

  for jar in jars:
    jar.close()

  return element


def _read_jvm_args(prefix, suffix, product_info):
    """Extracts and sorts JVM arguments that start with a prefix and end with a suffix.

    Args:
        prefix: The prefix string.
        suffix: The suffix string.
        product_info: A dictionary containing product information, including JVM arguments.

    Returns:
        A sorted list of strings containing the arguments without the prefix and suffix,
        or an empty list if no matching arguments are found.
    """
    jvm_args = product_info["launch"][0]["additionalJvmArguments"]
    result = []
    for arg in jvm_args:
        if arg.startswith(prefix) and arg.endswith(suffix):
            result.append(arg[len(prefix):-len(suffix)])
    result.sort()
    return result
