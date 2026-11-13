# Companion Quasar EtherCAT (resoem + resoes + OpcUaServerService) OCI Containerfile
# Build context must point to the sibling quasar repository root, with eniworkshop's
# quasar_server copied alongside it (see helpers/podman-compose.yml).
FROM docker.io/library/debian:trixie-slim AS build

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake git pkg-config python3 file ccache \
    autoconf automake libtool \
    libgmp-dev libssl-dev uuid-dev zlib1g-dev binutils-dev \
    libyaml-cpp-dev libtinyxml2-dev libjsoncons-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY quasar ./quasar
COPY eniworkshop/quasar_server ./eniworkshop_quasar_server

RUN mkdir -p /workspace/quasar/build && cd /workspace/quasar/build \
    && cmake .. -DCMAKE_BUILD_TYPE=Release -DQUASAR_BUILD_EXAMPLES=OFF -DQUASAR_BUILD_TESTS=OFF \
    && cmake --build . --target resoem resoes quasar_opcua quasar_named quasar_coretypes open62541 -j"$(nproc)"

RUN mkdir -p /workspace/eniworkshop_quasar_server/build && cd /workspace/eniworkshop_quasar_server/build \
    && cmake .. -DCMAKE_BUILD_TYPE=Release -DQUASAR_ROOT=/workspace/quasar \
    && cmake --build . -j"$(nproc)"

FROM docker.io/library/debian:trixie-slim AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
    libssl3 libuuid1 zlib1g libyaml-cpp0.8 libtinyxml2-11 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/eniworkshop_quasar_server/build/eniworkshop_quasar_server /app/eniworkshop_quasar_server

EXPOSE 4840

ENTRYPOINT ["/app/eniworkshop_quasar_server"]
