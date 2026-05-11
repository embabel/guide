# Standalone Guide backend image
# Uses jammy (not alpine) because ONNX Runtime needs libstdc++
# Expects guide-app.jar to be pre-built and placed in this directory

FROM eclipse-temurin:21-jre-jammy

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

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ParallelRefProcEnabled", \
  "-jar", "app.jar"]