#!/bin/bash
# ═══════════════════════════════════════════════════════
#  L2kt GameServer Launcher (macOS)
#  Double-click to start the game world server
# ═══════════════════════════════════════════════════════

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="$SCRIPT_DIR/build/dist/gameserver"

# Check if distribution exists
if [ ! -d "$DIST_DIR/libs" ]; then
    echo "ERROR: Distribution not found at $DIST_DIR"
    echo "Run './gradlew dist' first to build the distribution."
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

cd "$SCRIPT_DIR"

echo "═══════════════════════════════════════════════════════"
echo "  L2kt GameServer"
echo "  Port: 7777 (clients)"
echo "  Memory: 2 GB dedicated"
echo "═══════════════════════════════════════════════════════"
echo ""

# JVM flags for GameServer (heavy: 2 GB)
JAVA_OPTS="-Xms2g -Xmx2g"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:+ParallelRefProcEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=100"
JAVA_OPTS="$JAVA_OPTS -XX:+DisableExplicitGC"
JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.config.file=config/logging.properties"

# Classpath: all JARs in dist libs/
CP="$DIST_DIR/libs/*"

echo "Starting GameServer..."
echo "JVM: $JAVA_OPTS"
echo ""

java $JAVA_OPTS -cp "$CP" com.l2kt.gameserver.GameServer

EXIT_CODE=$?
echo ""
echo "GameServer stopped (exit code: $EXIT_CODE)"

# Exit code 2 = scheduled reboot
if [ $EXIT_CODE -eq 2 ]; then
    echo "Reboot requested. Restarting in 5 seconds..."
    sleep 5
    exec "$0"
fi

echo ""
read -p "Press Enter to close this window..."
