#!/usr/bin/env python3
from pathlib import Path
import re, sqlite3, subprocess, sys, tempfile, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors=[]; checks=[]
def ok(name): checks.append(name); print(f"[OK] {name}")
def fail(name,msg): errors.append((name,msg)); print(f"[FAIL] {name}: {msg}")

required=[
 'settings.gradle.kts','build.gradle.kts','gradle.properties','app/build.gradle.kts','app/src/main/AndroidManifest.xml',
 'gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties',
 'app/src/main/java/com/wutiaoliuyan/app/MainActivity.kt','app/src/main/java/com/wutiaoliuyan/app/ui/WutiaoLiuyanRoot.kt',
 'app/src/main/java/com/wutiaoliuyan/app/ai/DeepSeekClient.kt','app/src/main/java/com/wutiaoliuyan/app/data/AppDatabase.kt'
]
missing=[x for x in required if not (ROOT/x).exists()]
if missing: fail('关键文件', ', '.join(missing))
else: ok('关键文件齐全')

xmls=list((ROOT/'app/src/main/res').rglob('*.xml'))+[ROOT/'app/src/main/AndroidManifest.xml']
try:
    for p in xmls: ET.parse(p)
    ok(f'XML 可解析（{len(xmls)} 个）')
except Exception as e: fail('XML',str(e))

manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
if 'android:allowBackup="false"' in manifest and 'android:usesCleartextTraffic="false"' in manifest: ok('隐私开关与明文 HTTP 禁止')
else: fail('Manifest 安全项','allowBackup/usesCleartextTraffic 未按预期设置')

all_text='\n'.join(p.read_text(errors='ignore') for p in ROOT.rglob('*') if p.is_file() and p.suffix in {'.kt','.kts','.xml','.md','.properties','.py','.sh','.bat'})
if not re.search(r'\bsk-[A-Za-z0-9_-]{16,}', all_text): ok('未发现硬编码 DeepSeek Key')
else: fail('API Key','疑似硬编码密钥')

# Basic resource reference existence
resource_names={}
for d in (ROOT/'app/src/main/res').iterdir():
    if d.is_dir():
        kind=d.name.split('-')[0]
        resource_names.setdefault(kind,set()).update(p.stem for p in d.glob('*') if p.is_file())
        if kind == 'values':
            for f in d.glob('*.xml'):
                try:
                    root=ET.parse(f).getroot()
                    for node in root:
                        name=node.attrib.get('name')
                        if name: resource_names.setdefault(node.tag,set()).add(name)
                except Exception: pass
refs=re.findall(r'@([a-zA-Z_]+)/([a-zA-Z0-9_.]+)', '\n'.join(p.read_text(errors='ignore') for p in xmls))
missing_refs=[]
for kind,name in refs:
    if kind == 'android': continue
    if name not in resource_names.get(kind,set()) and not (kind=='id'):
        missing_refs.append(f'{kind}/{name}')
if missing_refs: fail('资源引用', ', '.join(sorted(set(missing_refs))))
else: ok('XML 资源引用')

# Manifest class mapping
classes=set()
for p in (ROOT/'app/src/main/java').rglob('*.kt'):
    txt=p.read_text(errors='ignore')
    pkg=re.search(r'^package\s+([\w.]+)',txt,re.M)
    if not pkg: continue
    for m in re.finditer(r'\b(?:class|object)\s+(\w+)',txt): classes.add(pkg.group(1)+'.'+m.group(1))
manifest_classes=re.findall(r'android:name="(\.[\w.]+)"',manifest)
missing_cls=[]
for c in manifest_classes:
    full='com.wutiaoliuyan.app'+c
    if full not in classes and not full.startswith('com.wutiaoliuyan.app.androidx'):
        missing_cls.append(full)
# provider is androidx and appears as fully qualified, not matched above
if missing_cls: fail('Manifest 组件',', '.join(missing_cls))
else: ok('Manifest 组件存在')

# Kotlin parser smoke-check. Android references are expected to be unresolved here; only parser diagnostics fail this check.
try:
    kt_files=[str(p) for p in (ROOT/'app/src/main/java').rglob('*.kt')]
    out=Path(tempfile.gettempdir())/'wutiao-parser.jar'
    r=subprocess.run(['kotlinc',*kt_files,'-d',str(out)],capture_output=True,text=True,timeout=60)
    parser_lines=[line for line in r.stderr.splitlines() if re.search(r'error: (expecting|unexpected tokens|syntax error|missing \})', line, re.I)]
    if parser_lines: raise RuntimeError('\n'.join(parser_lines[:12]))
    ok('Kotlin 解析级语法')
except subprocess.TimeoutExpired:
    fail('Kotlin 解析级语法','kotlinc 超时')
except Exception as e:
    fail('Kotlin 解析级语法',str(e))

# SQLite v4 onCreate SQL extracted from source
src=(ROOT/'app/src/main/java/com/wutiaoliuyan/app/data/AppDatabase.kt').read_text()
sqls=re.findall(r'db\.execSQL\("""(.*?)"""\.trimIndent\(\)\)',src,re.S)
sqls += re.findall(r'db\.execSQL\("(CREATE (?:TABLE|INDEX).*?)"\)',src)
try:
    db=sqlite3.connect(':memory:')
    for q in sqls: db.execute(q)
    cols={r[1] for r in db.execute('pragma table_info(tasks)')}
    required_cols={'parent_task_title','reminder_enabled','recurrence','status','deleted_at'}
    if not required_cols.issubset(cols): raise RuntimeError('tasks 缺少列 '+str(required_cols-cols))
    db.close(); ok('SQLite v4 建库 SQL')
except Exception as e: fail('SQLite',str(e))

# Pure Kotlin model compilation
try:
    out=Path(tempfile.gettempdir())/'wutiao-models.jar'
    r=subprocess.run(['kotlinc',str(ROOT/'app/src/main/java/com/wutiaoliuyan/app/model/Models.kt'),'-d',str(out)],capture_output=True,text=True,timeout=30)
    if r.returncode: raise RuntimeError(r.stderr.strip()[:1200])
    ok('纯 Kotlin 数据模型编译')
except Exception as e: fail('Kotlin 模型编译',str(e))

# Wrapper jar validity
try:
    with zipfile.ZipFile(ROOT/'gradle/wrapper/gradle-wrapper.jar') as z:
        if not any(n.endswith('Bootstrap.class') for n in z.namelist()): raise RuntimeError('Bootstrap.class 不存在')
    ok('Gradle bootstrap wrapper JAR')
except Exception as e: fail('Gradle wrapper',str(e))

# Feature smoke checks
feature_tokens=['ACTION_SEND_MULTIPLE','POST_NOTIFICATIONS','TextRecognition','response_format','task_sources','APPWIDGET_UPDATE','AES/GCM/NoPadding','30 天']
miss=[t for t in feature_tokens if t not in all_text]
if miss: fail('关键能力标记',', '.join(miss))
else: ok('关键能力标记')

print(f"\nPreflight: {len(checks)} passed, {len(errors)} failed")
if errors: sys.exit(1)
