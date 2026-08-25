#!/usr/bin/env sh
# ============================================================
#  DCF-Valuation-Tool launcher (Linux / macOS)
#  Usage:
#    ./run.sh           -> dev mode (mvn spring-boot:run, auto compile)
#    ./run.sh jar       -> run packaged jar (java -jar, run mvn package first)
#  Optional proxy for overseas data sources (FRED etc. only;
#  other users do NOT need any proxy config):
#    HTTPS_PROXY=http://127.0.0.1:7890 ./run.sh
# ============================================================
set -e
cd "$(dirname "$0")"

JAR="target/dcf-valuation-tool-1.1.1.jar"
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

if [ "$1" = "jar" ]; then
    if [ ! -f "$JAR" ]; then
        echo "[INFO] jar not found, building first (skip tests)..."
        mvn -q -DskipTests package
    fi
    # shellcheck disable=SC2086
    exec java $PROXY_ARGS -jar "$JAR"
fi

if [ -f "$JAR" ]; then
    echo "[INFO] packaged jar found, run it directly (mvn package first for latest code)"
    # shellcheck disable=SC2086
    exec java $PROXY_ARGS -jar "$JAR"
fi

echo "[INFO] dev mode (auto compile on first run)..."
exec mvn spring-boot:run "-Dspring-boot.run.jvmArguments=$PROXY_ARGS"