#!/usr/bin/env bash
# 构建 NeverLag 1.3.1（面向 Minecraft 1.7.10 / Crucible 服务端）
set -euo pipefail
cd "$(dirname "$0")"

SERVER_DIR="${SERVER_DIR:-D:/Game/Minecraft/Server/gtnh1/server2}"
JAVAC="${JAVAC:-D:/Library/DevelopmentKit/Java/Zulu/zulu8/bin/javac.exe}"
JAR="${JAR:-D:/Library/DevelopmentKit/Java/Zulu/zulu8/bin/jar.exe}"
CRUCIBLE="${CRUCIBLE:-$SERVER_DIR/Crucible.jar}"
PROTOCOLLIB="${PROTOCOLLIB:-$SERVER_DIR/plugins/ProtocolLib.jar}"
VERSION="1.5.4"
OUT="build"

for f in "$JAVAC" "$JAR" "$CRUCIBLE" "$PROTOCOLLIB"; do
    if [ ! -f "$f" ]; then
        echo "缺少依赖: $f" >&2
        exit 1
    fi
done

mkdir -p "$OUT/classes"
find src/main/java -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
    -cp "$CRUCIBLE;$PROTOCOLLIB" \
    -d "$OUT/classes" @"$OUT/sources.txt"
cp -r src/main/resources/. "$OUT/classes/"
(cd "$OUT/classes" && "$JAR" cf "../NeverLag${VERSION}.jar" .)

echo "产物: $OUT/NeverLag${VERSION}.jar"
