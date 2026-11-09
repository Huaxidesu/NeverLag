#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 GTNH 整合包自带的汉化里提取生物中文名，生成插件用的映射表。

数据来源（优先级从低到高，后读的覆盖先读的）：
  1. 原版 1.7.10 的 zh_CN.lang（Minecraft 客户端 assets）
  2. mods/*.jar 内 assets/<modid>/lang/zh_CN.lang
  3. config/txloader/forceload/*/lang/zh_CN.lang（GTNH 官方中文包，TXLoader）
  4. config/txloader/forceload/____gtnhoverridenames_zhcn（GTNH 对原版名词的改动，最高优先级）

只保留 `entity.<键>.name=<中文>` 形式的条目，输出 `<键>=<中文>` 的纯文本。
同一实体同时写入带 modid 前缀与去掉前缀的别名，运行时按 NMS EntityList 的键查找。

用法示例：
    python tools/gen_mobnames.py \
        --instance "D:/Game/Minecraft/MMCproject/instances/GTNHRC2/.minecraft" \
        --vanilla  "D:/Game/Minecraft/HMCL/.minecraft/assets/objects/eb/eb206c38395f02f84e1062af3fab9e05cb15611f" \
        --out      src/main/resources/mobnames_zh_cn.txt
"""

import argparse
import io
import os
import re
import sys
import zipfile

ENTITY_RE = re.compile(r"^entity\.([A-Za-z0-9_.\-]+)\.name=(.*)$")


def read_lang_text(text, mapping):
    """把一段 .lang 内容并入 mapping。"""
    added = 0
    for raw in text.splitlines():
        line = raw.strip().lstrip("\ufeff")
        if not line or line.startswith("#"):
            continue
        m = ENTITY_RE.match(line)
        if not m:
            continue
        key, value = m.group(1), m.group(2).strip()
        if not value:
            continue
        # 去掉 § 颜色代码
        if "\u00a7" in value:
            value = re.sub(r"\u00a7.", "", value)
        if value == key or value == "entity." + key + ".name":
            continue
        mapping[key] = value
        added += 1
    return added


def read_lang_file(path, mapping):
    for enc in ("utf-8-sig", "utf-8", "gbk"):
        try:
            with io.open(path, "r", encoding=enc) as fh:
                return read_lang_text(fh.read(), mapping)
        except UnicodeDecodeError:
            continue
        except OSError:
            return 0
    return 0


def read_jar_langs(jar_path, mapping):
    added = 0
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for name in zf.namelist():
                if not name.lower().endswith("lang/zh_cn.lang"):
                    continue
                try:
                    text = zf.read(name).decode("utf-8-sig", errors="replace")
                except Exception:
                    continue
                added += read_lang_text(text, mapping)
    except (zipfile.BadZipFile, OSError):
        return 0
    return added


def iter_txloader_langs(instance_dir):
    """按目录名排序返回 txloader 的 zh_CN.lang，保证 ____gtnhoverridenames_zhcn 最后读。"""
    root = os.path.join(instance_dir, "config", "txloader", "forceload")
    if not os.path.isdir(root):
        return []
    out = []
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name, "lang", "zh_CN.lang")
        if os.path.isfile(path):
            out.append(path)
    out.sort(key=lambda p: 1 if "gtnhoverridenames" in p.lower() else 0)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--instance", required=True, help="GTNH 实例的 .minecraft 目录")
    ap.add_argument("--vanilla", action="append", default=[],
                    help="原版 zh_CN.lang 路径，可多次指定")
    ap.add_argument("--mods", action="append", default=[],
                    help="mods 目录，可多次指定（默认 <instance>/mods）")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    mapping = {}

    for path in args.vanilla:
        if os.path.isfile(path):
            print("原版语言文件: %s (%d 条)" % (path, read_lang_file(path, mapping)))

    mods_dirs = args.mods or [os.path.join(args.instance, "mods")]
    jar_count = 0
    for mods_dir in mods_dirs:
        if not os.path.isdir(mods_dir):
            continue
        for name in sorted(os.listdir(mods_dir)):
            if not name.lower().endswith(".jar"):
                continue
            read_jar_langs(os.path.join(mods_dir, name), mapping)
            jar_count += 1
    print("扫描 mods jar: %d 个，累计 %d 条" % (jar_count, len(mapping)))

    for path in iter_txloader_langs(args.instance):
        read_lang_file(path, mapping)
    print("并入 txloader 汉化，累计 %d 条" % len(mapping))

    # 额外来源：实例根目录的 GregTech_zh_CN.lang
    gt = os.path.join(args.instance, "GregTech_zh_CN.lang")
    if os.path.isfile(gt):
        read_lang_file(gt, mapping)

    # 生成别名：带 modid 前缀与去掉前缀两种写法
    out = {}
    for key, value in mapping.items():
        out[key] = value
        if "." in key:
            out[key.split(".", 1)[1]] = value

    with io.open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("# 生物中文名映射，由 tools/gen_mobnames.py 从 GTNH 整合包汉化生成\n")
        fh.write("# 格式: <EntityList键>=<中文名>，键不区分大小写\n")
        for key in sorted(out):
            fh.write("%s=%s\n" % (key, out[key]))
    print("写出 %s：%d 条（含别名）" % (args.out, len(out)))


if __name__ == "__main__":
    sys.exit(main())
