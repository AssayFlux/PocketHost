# Build the Vite console once, then serve only the static production bundle.
FROM node:22-alpine AS build
WORKDIR /workspace/web-console
COPY web-console/package.json web-console/package-lock.json ./
RUN npm ci
COPY web-console/ ./
ARG VITE_POCKETHOST_CONTROL_API_BASE
ENV VITE_POCKETHOST_CONTROL_API_BASE=${VITE_POCKETHOST_CONTROL_API_BASE}
RUN npm run build

FROM nginx:1.27-alpine
COPY web-console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-console/dist/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
