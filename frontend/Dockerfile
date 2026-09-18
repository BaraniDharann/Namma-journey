# Stage 1: Build
FROM node:20-alpine AS build
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
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
