# ---- Build: install exact dependencies (package-lock.json) and produce static files -------------------
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Serve: nginx as a non-root user, static files only, no Node.js at runtime ---------------------------
FROM nginxinc/nginx-unprivileged:1.29-alpine
# Links the published image (GHCR) to this repository.
LABEL org.opencontainers.image.source="https://github.com/bossshhh/stockflow" \
      org.opencontainers.image.description="StockFlow web UI (nginx)"
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# nginx-unprivileged listens on 8080 (ports below 1024 need root).
EXPOSE 8080
