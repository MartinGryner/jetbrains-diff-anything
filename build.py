#!/usr/bin/env python3
"""Build against an extracted official IDE SDK; no changes to the installed IDE."""
import argparse, json, os, pathlib, shutil, subprocess, zipfile

root = pathlib.Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument('--ide', required=True, type=pathlib.Path, help='IDE home, or macOS .app/Contents')
p.add_argument('--jdk', required=True, type=pathlib.Path, help='JDK 21 or newer with javac and java')
a = p.parse_args()
build = root / 'build'
classes = build / 'classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir(parents=True)
jars = sorted((a.ide / 'lib').rglob('*.jar'))
cp = os.pathsep.join(map(str, jars))
sources = sorted((root / 'src/main/java').rglob('*.java'))
subprocess.run([str(a.jdk/'bin/javac'), '--release', '21', '-encoding', 'UTF-8', '-classpath', cp, '-d', str(classes), *map(str,sources)], check=True)
shutil.copytree(root/'src/main/resources', classes, dirs_exist_ok=True)
jar = build/'compare-with-diff-anything.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in sorted(classes.rglob('*')):
        if file.is_file(): z.write(file, file.relative_to(classes).as_posix())
archive = build/'compare-with-diff-anything-0.1.0.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, 'compare-with-diff-anything/lib/'+jar.name)
test_classes = build/'test-classes'
test_classes.mkdir(exist_ok=True)
tests = sorted((root/'src/test/java').rglob('*.java'))
subprocess.run([str(a.jdk/'bin/javac'), '--release', '21', '-encoding', 'UTF-8', '-classpath', str(classes), '-d', str(test_classes), *map(str,tests)], check=True)
subprocess.run([str(a.jdk/'bin/java'), '-ea', '-classpath', os.pathsep.join([str(classes),str(test_classes)]), 'com.martingruner.diffanything.LocalComparisonTest'], check=True)
print(archive)
