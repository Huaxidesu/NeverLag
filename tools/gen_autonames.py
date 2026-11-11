# -*- coding: utf-8 -*-
"""从 GTNH 汉化包提取「模组自动拼出来的名字词」，生成 autoname_zh_cn.txt。

用途：服务端的清理插件要判断一个 `CustomName` 是玩家起的还是模组生成的。
玩家起名（旺财、小白）没有规律可循，但模组拼名有：InfernalMobs 的精英怪名字
就是「前缀 + 生物名」或「生物名 + 后缀」，前缀后缀全在它自己的语言文件里。

输入：汉化包目录（nightly-*.7z 解压后的 `config/txloader/forceload/**/lang/zh_CN.lang`）
输出：一行一个词条，`#` 开头为注释

用法：
  python tools/gen_autonames.py <汉化包目录> <输出文件>

词条来源：
  translation.infernalmobs:prefix.*   69 条，「剧毒的」「近乎无敌的」这类前缀
  translation.infernalmobs:suffix.*   73 条，「不死者」「狂怒者」这类后缀
`rareClass` / `infernalClass` / `ultraClass` 三个是等级标签（稀有 / 恶魔 / 精英），
不是名字的一部分，不收录。
"""
import io
import os
import sys

SRC = sys.argv[1]
OUT = sys.argv[2]

prefixes, suffixes = [], []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if not name.endswith(".lang"):
            continue
        path = os.path.join(dirpath, name)
        if "infernalmobs" not in path.lower():
            continue
        for raw in io.open(path, encoding="utf-8", errors="replace"):
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key, value = key.strip().lower(), value.strip()
            if not value:
                continue
            if ":prefix." in key:
                prefixes.append(value)
            elif ":suffix." in key:
                suffixes.append(value)

# 去重但保持顺序
def unique(items):
    seen, out = set(), []
    for item in items:
        if item not in seen:
            seen.add(item)
            out.append(item)
    return out

prefixes = unique(prefixes)
suffixes = unique(suffixes)

lines = [
    "# 模组自动拼名用到的词（InfernalMobs 精英怪前缀 / 后缀），由 tools/gen_autonames.py 从汉化包生成。",
    "# 一行一个，命中即认为名字不是玩家起的；不要在中间加空格。",
    "# --- 前缀 ---",
]
lines += prefixes
lines.append("# --- 后缀 ---")
lines += suffixes
io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
print("前缀 %d 条，后缀 %d 条 -> %s" % (len(prefixes), len(suffixes), OUT))
