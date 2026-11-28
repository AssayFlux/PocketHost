FROM rust:1.88-bookworm AS builder
WORKDIR /workspace
COPY control-plane ./control-plane
WORKDIR /workspace/control-plane
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl     && rm -rf /var/lib/apt/lists/*     && useradd --system --uid 10001 --no-create-home pockethost
COPY --from=builder /workspace/control-plane/target/release/pockethost-control-plane /usr/local/bin/pockethost-control-plane
RUN mkdir -p /var/lib/pockethost/artifacts && chown -R 10001:10001 /var/lib/pockethost
USER 10001:10001
ENV POCKETHOST_CONTROL_BIND=0.0.0.0:8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 CMD curl -fsS http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["/usr/local/bin/pockethost-control-plane"]
