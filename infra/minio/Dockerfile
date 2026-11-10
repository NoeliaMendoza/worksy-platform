# MinIO ya no publica imágenes comunitarias (Docker Hub y quay.io dejaron de servirlas).
# La vía oficial para la edición comunitaria es compilar desde el código fuente (AGPLv3).
# Se fijan etiquetas de versión para que la imagen sea reproducible.
FROM golang:1.24 AS build
ARG MINIO_TAG=RELEASE.2025-10-15T17-29-55Z
ARG MC_TAG=RELEASE.2025-08-13T08-35-41Z
ENV CGO_ENABLED=0
WORKDIR /src
RUN git clone --depth 1 --branch ${MINIO_TAG} https://github.com/minio/minio.git \
    && cd minio && go build -trimpath -ldflags "-s -w" -o /out/minio .
RUN git clone --depth 1 --branch ${MC_TAG} https://github.com/minio/mc.git \
    && cd mc && go build -trimpath -ldflags "-s -w" -o /out/mc .

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home minio && mkdir /data && chown minio /data
COPY --from=build /out/minio /out/mc /usr/local/bin/
USER minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
