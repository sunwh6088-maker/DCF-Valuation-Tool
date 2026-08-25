#!/usr/bin/env sh
# ============================================================
#  DCF-Valuation-Tool launcher (Linux / macOS)
#  Usage:
#    ./run.sh            -> dev mode (auto compile, prefers bundled mvnw, no Maven install needed)
#    ./run.sh jar        -> run packaged jar (java -jar, run mvn package first)
#    ./run.sh 8502       -> dev mode + custom port (when 8501 is occupied)
#    ./run.sh jar 8502   -> run jar + custom port
#  Optional proxy for overseas data sources (FRED etc. only;
#  other users do NOT need any proxy config):
#    HTTPS_PROXY=http://127.0.0.1:7890 ./run.sh
# ============================================================
set -e
cd "$(dirname "$0")"

# ---------- Java check (JDK 21+ required, friendly hint if missing) ----------
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java not found. Please install JDK 21 first: https://adoptium.net/temurin/releases/"
    exit 1
fi
JAVA_MAJOR="$(java -version 2>&1 | awk -F'["\\.]' '/version/{gsub(/[^0-9]/,"",$2); print $2; exit}')"
case "$JAVA_MAJOR" in
    ''|*[!0-9]*) echo "[ERROR] Cannot parse Java version. Please install JDK 21+."; exit 1 ;;
esac
if [ "$JAVA_MAJOR" -lt 21 ]; then
    echo "[ERROR] JDK 21+ required, found $JAVA_MAJOR"
    exit 1
fi

# ---------- Maven: prefer bundled wrapper (no manual Maven install needed) ----------
MVN="mvn"
[ -f "./mvnw" ] && [ -x "./mvnw" ] && MVN="./mvnw"

JAR="target/dcf-valuation-tool-1.1.8.jar"
PROXY_ARGS=""

PROXY_URL="${HTTPS_PROXY:-$HTTP_PROXY}"
if [ -n "$PROXY_URL" ]; then
    HOST="$(printf '%s' "$PROXY_URL" | sed -E 's#^[a-zA-Z]+://##; s#[:/].*$##')"
    PORT="$(printf '%s' "$PROXY_URL" | sed -E 's#^[a-zA-Z]+://[^:]*:?([0-9]+).*$#\1#')"
    case "$PORT" in
        ''|*[!0-9]*) PORT=80 ;;
    esac
    if [ -n "$HOST" ]; then
        PROXY_ARGS="-Dhttps.proxyHost=$HOST -Dhttps.proxyPort=$PORT -Dhttp.proxyHost=$HOST -Dhttp.proxyPort=$PORT"
        echo "[INFO] using proxy: $PROXY_URL"
    fi
fi

# Optional port: ./run.sh [jar] [port]
PORT_ARG=""
DEV_ARGS=""
case "$1" in
    jar) [ -n "$2" ] && PORT_ARG="--server.port=$2" ;;
    *) [ -n "$1" ] && PORT_ARG="--server.port=$1" ;;
esac
[ -n "$PORT_ARG" ] && DEV_ARGS="-Dspring-boot.run.arguments=$PORT_ARG"

if [ "$1" = "jar" ]; then
    if [ ! -f "$JAR" ]; then
        echo "[INFO] jar not found, building first (skip tests)..."
        "$MVN" -q -DskipTests package
    fi
    # shellcheck disable=SC2086
    exec java $PROXY_ARGS -jar "$JAR" $PORT_ARG
fi

if [ -f "$JAR" ]; then
    echo "[INFO] packaged jar found, run it directly (mvn package first for latest code)"
    # shellcheck disable=SC2086
    exec java $PROXY_ARGS -jar "$JAR" $PORT_ARG
fi

echo "[INFO] dev mode (auto compile on first run)..."
# shellcheck disable=SC2086
exec "$MVN" spring-boot:run "-Dspring-boot.run.jvmArguments=$PROXY_ARGS" $DEV_ARGS


