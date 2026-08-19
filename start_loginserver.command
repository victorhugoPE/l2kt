#!/bin/bash
# ═══════════════════════════════════════════════════════
#  L2kt LoginServer Launcher (macOS)
#  Double-click to start the authentication server
# ═══════════════════════════════════════════════════════

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="$SCRIPT_DIR/build/dist/login"

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
echo "  L2kt LoginServer"
echo "  Port: 2106 (clients) | 9014 (gameserver)"
echo "═══════════════════════════════════════════════════════"
echo ""

# JVM flags for LoginServer (lightweight: 512 MB max)
JAVA_OPTS="-Xms256m -Xmx512m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:+ParallelRefProcEnabled"
JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.config.file=config/logging.properties"

# Classpath: all JARs in dist libs/
CP="$DIST_DIR/libs/*"

echo "Starting LoginServer..."
echo "JVM: $JAVA_OPTS"
echo ""

java $JAVA_OPTS -cp "$CP" com.l2kt.loginserver.LoginServer

EXIT_CODE=$?
echo ""
echo "LoginServer stopped (exit code: $EXIT_CODE)"
echo ""
read -p "Press Enter to close this window..."
