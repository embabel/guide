# Standalone Guide backend image
# Uses jammy (not alpine) because ONNX Runtime needs libstdc++
# Expects guide-app.jar to be pre-built and placed in this directory

FROM eclipse-temurin:25-jre-jammy

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Pre-download ONNX embedding model (HuggingFace redirects break JDK default HTTP client)
RUN mkdir -p /root/.embabel/models/all-MiniLM-L6-v2 && \
    curl -L -o /root/.embabel/models/all-MiniLM-L6-v2/model.onnx \
      https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx && \
    curl -L -o /root/.embabel/models/all-MiniLM-L6-v2/tokenizer.json \
      https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json

COPY guide-app.jar app.jar

EXPOSE 1337

# Both flags below require the JDK 25 base image pinned above:
# --enable-native-access exists from JDK 22, --sun-misc-unsafe-memory-access from JDK 23.
# The jar itself is Java 21 bytecode and runs on any JRE 21+, but if you drop this image
# back to a 21 JRE you must remove these two lines or the JVM will refuse to start.
#
# --enable-native-access: ONNX Runtime and DJL tokenizers call System::load; without this
#   JDK 25 warns, and a future JDK will refuse the call outright.
# --sun-misc-unsafe-memory-access=allow: Netty still uses sun.misc.Unsafe memory access,
#   which JDK 25 warns about on first use. Drop this once Netty no longer needs it.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ParallelRefProcEnabled", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]