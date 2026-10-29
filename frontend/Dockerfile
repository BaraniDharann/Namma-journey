# Stage 1: Build
# On the builder's own CPU even when the image is for another one (CI makes amd64 + arm64): the
# bundle is plain static files, identical for both, and npm under emulation is very slow.
FROM --platform=$BUILDPLATFORM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Vite inlines VITE_* into the bundle at build time, so they must arrive as build args, not
# runtime env vars — the container never runs `vite`, only nginx serving the already-built files.
ARG VITE_API_BASE_URL
ARG VITE_GOOGLE_CLIENT_ID
ARG VITE_OSRM_BASE_URL
ARG VITE_PHOTON_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_OSRM_BASE_URL=$VITE_OSRM_BASE_URL \
    VITE_PHOTON_BASE_URL=$VITE_PHOTON_BASE_URL
# Node's default heap ran out bundling ~5.6k modules on a 2 GB server (swap covers the rest).
RUN NODE_OPTIONS=--max-old-space-size=2048 npm run build

# Stage 2: Serve
# Pinned: nginx.conf's upstream uses "server ... resolve", open-source only since 1.27.3, and an
# unpinned tag can move to a release that changes behaviour under us.
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
