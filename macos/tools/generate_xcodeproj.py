#!/usr/bin/env python3
"""Generates macos/AkaaFlix.xcodeproj (project.pbxproj + shared scheme) from the source tree.

Targets
  • "Akaa Flix"      macOS application (SwiftUI + AppKit), embeds VLCKit.xcframework
  • "AkaaFlixTests"  XCTest bundle compiling AkaaFlix/Core + AkaaFlixTests (no app host)

Configurations: Development, Testing, Release (app settings live in macos/Config/*.xcconfig).
Object IDs are derived from stable keys, so regenerating produces identical files.

Usage: python3 macos/tools/generate_xcodeproj.py
"""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]           # macos/
PROJECT = ROOT / "AkaaFlix.xcodeproj"
APP_DIR = "AkaaFlix"
TEST_DIR = "AkaaFlixTests"
CONFIGS = ["Development", "Testing", "Release"]


def oid(key: str) -> str:
    return hashlib.md5(key.encode("utf-8")).hexdigest()[:24].upper()


def q(s: str) -> str:
    """Quotes a pbxproj string when needed."""
    if s and all(c.isalnum() or c in "._/$" for c in s) and not s[0].isdigit():
        return s
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


FILE_TYPES = {
    ".swift": "sourcecode.swift",
    ".plist": "text.plist.xml",
    ".entitlements": "text.plist.entitlements",
    ".xcconfig": "text.xcconfig",
    ".xcassets": "folder.assetcatalog",
    ".txt": "text",
    ".md": "net.daringfireball.markdown",
}

objects = {}          # id -> (section, comment, body)


def add(section, ident, comment, body):
    objects[ident] = (section, comment, body)


class Group:
    def __init__(self, key, name=None, path=None, source_tree="<group>"):
        self.id = oid("group:" + key)
        self.name, self.path, self.source_tree = name, path, source_tree
        self.children = []            # (id, comment)

    def emit(self):
        lines = ["isa = PBXGroup;", "children = ("]
        lines += [f"\t{cid} /* {c} */," for cid, c in self.children]
        lines.append(");")
        if self.name:
            lines.append(f"name = {q(self.name)};")
        if self.path:
            lines.append(f"path = {q(self.path)};")
        lines.append(f"sourceTree = {q(self.source_tree)};")
        add("PBXGroup", self.id, self.name or self.path or "", lines)


def file_ref(key, path, name=None, source_tree="<group>", ftype=None, explicit=None):
    ident = oid("file:" + key)
    body = ["isa = PBXFileReference;"]
    if explicit:
        body += [f"explicitFileType = {explicit};", "includeInIndex = 0;"]
    else:
        body.append(f"lastKnownFileType = {ftype};")
    if name:
        body.append(f"name = {q(name)};")
    body += [f"path = {q(path)};", f"sourceTree = {q(source_tree)};"]
    add("PBXFileReference", ident, name or Path(path).name, body)
    return ident


def build_file(key, ref, comment, phase, settings=None):
    ident = oid("build:" + key + ":" + phase)
    body = ["isa = PBXBuildFile;", f"fileRef = {ref} /* {comment} */;"]
    if settings:
        body.append(f"settings = {settings};")
    add("PBXBuildFile", ident, f"{comment} in {phase}", body)
    return ident


app_sources, test_sources, app_resources = [], [], []


def walk(directory: Path, group: Group, rel: str):
    for item in sorted(directory.iterdir(), key=lambda p: (p.suffix == ".xcassets" or not p.is_dir(), p.name.lower())):
        if item.name.startswith("."):
            continue
        item_rel = f"{rel}/{item.name}"
        if item.is_dir() and item.suffix != ".xcassets":
            g = Group(item_rel, path=item.name)
            walk(item, g, item_rel)
            if g.children:  # git does not track empty folders; keep the project identical everywhere
                g.emit()
                group.children.append((g.id, item.name))
            continue
        ftype = FILE_TYPES.get(item.suffix)
        if not ftype:
            continue
        ref = file_ref(item_rel, item.name, ftype=ftype)
        group.children.append((ref, item.name))
        if item.suffix == ".swift":
            if rel.startswith(APP_DIR):
                app_sources.append((item_rel, ref, item.name))
                if rel.startswith(f"{APP_DIR}/Core"):
                    test_sources.append((item_rel, ref, item.name))
            elif rel.startswith(TEST_DIR):
                test_sources.append((item_rel, ref, item.name))
        elif item.suffix in (".xcassets", ".txt") and rel.startswith(APP_DIR):
            app_resources.append((item_rel, ref, item.name))


def main():
    main_group = Group("main")
    app_group = Group(APP_DIR, path=APP_DIR)
    walk(ROOT / APP_DIR, app_group, APP_DIR)
    app_group.emit()
    test_group = Group(TEST_DIR, path=TEST_DIR)
    walk(ROOT / TEST_DIR, test_group, TEST_DIR)
    test_group.emit()

    config_group = Group("Config", path="Config")
    config_refs = {}
    for name in CONFIGS + ["Base", "Signing.example"]:
        ref = file_ref(f"Config/{name}.xcconfig", f"{name}.xcconfig", ftype="text.xcconfig")
        config_group.children.append((ref, f"{name}.xcconfig"))
        config_refs[name] = ref
    config_group.emit()

    vlc = file_ref("Vendor/VLCKit.xcframework", "Vendor/VLCKit.xcframework", name="VLCKit.xcframework",
                   source_tree="SOURCE_ROOT", ftype="wrapper.xcframework")
    frameworks_group = Group("Frameworks", name="Frameworks")
    frameworks_group.children.append((vlc, "VLCKit.xcframework"))
    frameworks_group.emit()

    app_product = file_ref("product:app", "Akaa Flix.app", source_tree="BUILT_PRODUCTS_DIR", explicit="wrapper.application")
    test_product = file_ref("product:tests", "AkaaFlixTests.xctest", source_tree="BUILT_PRODUCTS_DIR", explicit="wrapper.cfbundle")
    products = Group("Products", name="Products")
    products.children += [(app_product, "Akaa Flix.app"), (test_product, "AkaaFlixTests.xctest")]
    products.emit()

    main_group.children = [(app_group.id, APP_DIR), (test_group.id, TEST_DIR), (config_group.id, "Config"),
                           (frameworks_group.id, "Frameworks"), (products.id, "Products")]
    main_group.emit()

    # Build phases
    def phase(isa, key, name, files, extra=None):
        ident = oid("phase:" + key)
        body = [f"isa = {isa};", "buildActionMask = 2147483647;"]
        if extra:
            body += extra
        body.append("files = (")
        body += [f"\t{f} /* {c} */," for f, c in files]
        body += [");"]
        if isa == "PBXCopyFilesBuildPhase":
            body.append(f"name = {q(name)};")
        body.append("runOnlyForDeploymentPostprocessing = 0;")
        add(isa, ident, name, body)
        return ident

    app_src_files = [(build_file(k, r, n, "Sources"), f"{n} in Sources") for k, r, n in app_sources]
    test_src_files = [(build_file("test:" + k, r, n, "Sources"), f"{n} in Sources") for k, r, n in test_sources]
    res_files = [(build_file(k, r, n, "Resources"), f"{n} in Resources") for k, r, n in app_resources]
    vlc_link = build_file("vlc", vlc, "VLCKit.xcframework", "Frameworks")
    vlc_embed = build_file("vlc", vlc, "VLCKit.xcframework", "Embed Frameworks",
                           settings="{ATTRIBUTES = (CodeSignOnCopy, RemoveHeadersOnCopy, ); }")

    app_phases = [
        (phase("PBXSourcesBuildPhase", "app:sources", "Sources", app_src_files), "Sources"),
        (phase("PBXFrameworksBuildPhase", "app:frameworks", "Frameworks", [(vlc_link, "VLCKit.xcframework in Frameworks")]), "Frameworks"),
        (phase("PBXResourcesBuildPhase", "app:resources", "Resources", res_files), "Resources"),
        (phase("PBXCopyFilesBuildPhase", "app:embed", "Embed Frameworks", [(vlc_embed, "VLCKit.xcframework in Embed Frameworks")],
               extra=['dstPath = "";', "dstSubfolderSpec = 10;"]), "Embed Frameworks"),
    ]
    test_phases = [
        (phase("PBXSourcesBuildPhase", "test:sources", "Sources", test_src_files), "Sources"),
        (phase("PBXFrameworksBuildPhase", "test:frameworks", "Frameworks", []), "Frameworks"),
        (phase("PBXResourcesBuildPhase", "test:resources", "Resources", []), "Resources"),
    ]

    # Build configurations
    project_common = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ANALYZER_NONNULL": "YES",
        "CLANG_CXX_LANGUAGE_STANDARD": '"gnu++20"',
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "CLANG_WARN_UNGUARDED_AVAILABILITY": "YES_AGGRESSIVE",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "GCC_C_LANGUAGE_STANDARD": "gnu17",
        "MACOSX_DEPLOYMENT_TARGET": "11.0",
        "SDKROOT": "macosx",
        "SWIFT_VERSION": "5.0",
    }
    per_config_project = {
        "Development": {"DEBUG_INFORMATION_FORMAT": "dwarf", "ONLY_ACTIVE_ARCH": "YES", "SWIFT_OPTIMIZATION_LEVEL": '"-Onone"',
                        "ENABLE_TESTABILITY": "YES", "GCC_OPTIMIZATION_LEVEL": "0", "MTL_ENABLE_DEBUG_INFO": "INCLUDE_SOURCE"},
        "Testing": {"DEBUG_INFORMATION_FORMAT": "dwarf", "ONLY_ACTIVE_ARCH": "YES", "SWIFT_OPTIMIZATION_LEVEL": '"-Onone"',
                    "ENABLE_TESTABILITY": "YES", "GCC_OPTIMIZATION_LEVEL": "0"},
        "Release": {"DEBUG_INFORMATION_FORMAT": '"dwarf-with-dsym"', "SWIFT_COMPILATION_MODE": "wholemodule",
                    "ENABLE_NS_ASSERTIONS": "NO", "MTL_ENABLE_DEBUG_INFO": "NO"},
    }
    test_common = {
        "PRODUCT_NAME": "AkaaFlixTests",
        "PRODUCT_BUNDLE_IDENTIFIER": "com.akaaflix.mac.tests",
        "GENERATE_INFOPLIST_FILE": "YES",
        "MACOSX_DEPLOYMENT_TARGET": "11.0",
        "SWIFT_VERSION": "5.0",
        "CODE_SIGN_STYLE": "Manual",
        "CODE_SIGN_IDENTITY": '"-"',
        "ENABLE_HARDENED_RUNTIME": "NO",
        "LD_RUNPATH_SEARCH_PATHS": '"$(inherited) @executable_path/../Frameworks @loader_path/../Frameworks"',
        "SWIFT_EMIT_LOC_STRINGS": "NO",
        "MARKETING_VERSION": "4.0.7",
        "CURRENT_PROJECT_VERSION": "407",
    }

    def config(key, name, settings, base_ref=None, base_name=None):
        ident = oid("config:" + key + ":" + name)
        body = ["isa = XCBuildConfiguration;"]
        if base_ref:
            body.append(f"baseConfigurationReference = {base_ref} /* {base_name} */;")
        body.append("buildSettings = {")
        body += [f"\t{k} = {v};" for k, v in sorted(settings.items())]
        body += ["};", f"name = {name};"]
        add("XCBuildConfiguration", ident, name, body)
        return ident

    def config_list(key, comment, ids):
        ident = oid("configlist:" + key)
        body = ["isa = XCConfigurationList;", "buildConfigurations = ("]
        body += [f"\t{i} /* {n} */," for i, n in ids]
        body += [");", "defaultConfigurationIsVisible = 0;", "defaultConfigurationName = Release;"]
        add("XCConfigurationList", ident, comment, body)
        return ident

    proj_configs = [(config("project", n, {**project_common, **per_config_project[n]}), n) for n in CONFIGS]
    app_configs = [(config("app", n, {}, config_refs[n], f"{n}.xcconfig"), n) for n in CONFIGS]
    test_extra = {"Development": {"SWIFT_ACTIVE_COMPILATION_CONDITIONS": '"DEBUG"', "ONLY_ACTIVE_ARCH": "YES"},
                  "Testing": {"SWIFT_ACTIVE_COMPILATION_CONDITIONS": '"DEBUG TESTING"', "ONLY_ACTIVE_ARCH": "YES"},
                  "Release": {"ARCHS": '"arm64 x86_64"', "ONLY_ACTIVE_ARCH": "NO"}}
    test_configs = [(config("tests", n, {**test_common, **test_extra[n]}), n) for n in CONFIGS]
    proj_list = config_list("project", 'Build configuration list for PBXProject "AkaaFlix"', proj_configs)
    app_list = config_list("app", 'Build configuration list for PBXNativeTarget "Akaa Flix"', app_configs)
    test_list = config_list("tests", 'Build configuration list for PBXNativeTarget "AkaaFlixTests"', test_configs)

    def target(key, name, product_name, product_ref, product_file, ptype, phases, clist):
        ident = oid("target:" + key)
        body = ["isa = PBXNativeTarget;", f"buildConfigurationList = {clist} /* Build configuration list for PBXNativeTarget \"{name}\" */;",
                "buildPhases = ("]
        body += [f"\t{p} /* {c} */," for p, c in phases]
        body += [");", "buildRules = (", ");", "dependencies = (", ");", f"name = {q(name)};",
                 f"productName = {q(product_name)};", f"productReference = {product_ref} /* {product_file} */;",
                 f'productType = "{ptype}";']
        add("PBXNativeTarget", ident, name, body)
        return ident

    app_target = target("app", "Akaa Flix", "Akaa Flix", app_product, "Akaa Flix.app", "com.apple.product-type.application", app_phases, app_list)
    test_target = target("tests", "AkaaFlixTests", "AkaaFlixTests", test_product, "AkaaFlixTests.xctest",
                         "com.apple.product-type.bundle.unit-test", test_phases, test_list)

    project_id = oid("project")
    add("PBXProject", project_id, "Project object", [
        "isa = PBXProject;",
        "attributes = {",
        "\tBuildIndependentTargetsInParallel = 1;",
        "\tLastSwiftUpdateCheck = 1600;",
        "\tLastUpgradeCheck = 1600;",
        "\tTargetAttributes = {",
        f"\t\t{app_target} = {{",
        "\t\t\tCreatedOnToolsVersion = 16.0;",
        "\t\t};",
        f"\t\t{test_target} = {{",
        "\t\t\tCreatedOnToolsVersion = 16.0;",
        "\t\t};",
        "\t};",
        "};",
        f'buildConfigurationList = {proj_list} /* Build configuration list for PBXProject "AkaaFlix" */;',
        'compatibilityVersion = "Xcode 14.0";',
        "developmentRegion = ar;",
        "hasScannedForEncodings = 0;",
        "knownRegions = (",
        "\tar,",
        "\ten,",
        "\tBase,",
        ");",
        f"mainGroup = {main_group.id};",
        f"productRefGroup = {products.id} /* Products */;",
        'projectDirPath = "";',
        'projectRoot = "";',
        "targets = (",
        f"\t{app_target} /* Akaa Flix */,",
        f"\t{test_target} /* AkaaFlixTests */,",
        ");",
    ])

    order = ["PBXBuildFile", "PBXCopyFilesBuildPhase", "PBXFileReference", "PBXFrameworksBuildPhase", "PBXGroup",
             "PBXNativeTarget", "PBXProject", "PBXResourcesBuildPhase", "PBXSourcesBuildPhase",
             "XCBuildConfiguration", "XCConfigurationList"]
    out = ["// !$*UTF8*$!", "{", "\tarchiveVersion = 1;", "\tclasses = {", "\t};", "\tobjectVersion = 56;", "\tobjects = {", ""]
    for section in order:
        items = sorted((i, c, b) for i, (s, c, b) in objects.items() if s == section)
        if not items:
            continue
        out.append(f"/* Begin {section} section */")
        for ident, comment, body in items:
            if section in ("PBXBuildFile", "PBXFileReference"):
                out.append(f"\t\t{ident} /* {comment} */ = {{{' '.join(body)} }};")
            else:
                out.append(f"\t\t{ident} /* {comment} */ = {{")
                out += [f"\t\t\t{line}" for line in body]
                out.append("\t\t};")
        out.append(f"/* End {section} section */")
        out.append("")
    out += ["\t};", f"\trootObject = {project_id} /* Project object */;", "}", ""]

    PROJECT.mkdir(exist_ok=True)
    (PROJECT / "project.pbxproj").write_text("\n".join(out), encoding="utf-8")
    ws = PROJECT / "project.xcworkspace"
    ws.mkdir(exist_ok=True)
    (ws / "contents.xcworkspacedata").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n<Workspace\n   version = "1.0">\n   <FileRef\n      location = "self:">\n   </FileRef>\n</Workspace>\n',
        encoding="utf-8")
    write_scheme(app_target, test_target)
    print(f"Wrote {PROJECT} — {len(app_sources)} app sources, {len(test_sources)} test sources, {len(app_resources)} resources")


def write_scheme(app_target, test_target):
    app_ref = f'''<BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{app_target}"
               BuildableName = "Akaa Flix.app"
               BlueprintName = "Akaa Flix"
               ReferencedContainer = "container:AkaaFlix.xcodeproj">
            </BuildableReference>'''
    test_ref = f'''<BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{test_target}"
               BuildableName = "AkaaFlixTests.xctest"
               BlueprintName = "AkaaFlixTests"
               ReferencedContainer = "container:AkaaFlix.xcodeproj">
            </BuildableReference>'''
    scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1600"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            {app_ref}
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Testing"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
         <TestableReference
            skipped = "NO">
            {test_ref}
         </TestableReference>
      </Testables>
   </TestAction>
   <LaunchAction
      buildConfiguration = "Development"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         {app_ref}
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction
      buildConfiguration = "Release"
      shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = ""
      useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         {app_ref}
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction
      buildConfiguration = "Development">
   </AnalyzeAction>
   <ArchiveAction
      buildConfiguration = "Release"
      revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
'''
    d = PROJECT / "xcshareddata" / "xcschemes"
    d.mkdir(parents=True, exist_ok=True)
    (d / "Akaa Flix.xcscheme").write_text(scheme, encoding="utf-8")


if __name__ == "__main__":
    main()
