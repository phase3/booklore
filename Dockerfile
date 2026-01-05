# Stage 1: Build the Angular app
# Build on native platform (ARM) to avoid QEMU emulation issues with esbuild
# The output is just static files, so platform doesn't matter for the artifacts
FROM --platform=$BUILDPLATFORM node:20-bookworm AS angular-build

WORKDIR /angular-app

# Copy package files
COPY ./booklore-ui/package.json ./booklore-ui/package-lock.json ./

# Install dependencies
RUN npm ci --force

COPY ./booklore-ui /angular-app/

# Increase Node.js memory limit for Angular build
ENV NODE_OPTIONS="--max-old-space-size=8192"
ENV NODE_ENV=production

RUN npm run build -- --configuration=production

# Stage 2: Build the Spring Boot app with Gradle
# Build on native platform - JAR files are platform-independent
FROM --platform=$BUILDPLATFORM gradle:8.14.3-jdk21-alpine AS springboot-build

WORKDIR /springboot-app

# Copy only build files first to cache dependencies
COPY ./booklore-api/build.gradle ./booklore-api/settings.gradle /springboot-app/

# Download dependencies (cached layer)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon

COPY ./booklore-api/src /springboot-app/src

# Inject version into application.yaml using yq
ARG APP_VERSION
RUN apk add --no-cache yq && \
    yq eval '.app.version = strenv(APP_VERSION)' -i /springboot-app/src/main/resources/application.yaml

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle clean build -x test --no-daemon --parallel

# Stage 3: Final image
FROM eclipse-temurin:21.0.9_10-jre-alpine

ARG APP_VERSION
ARG APP_REVISION

# Set OCI labels
LABEL org.opencontainers.image.title="BookLore" \
      org.opencontainers.image.description="BookLore: A self-hosted, multi-user digital library with smart shelves, auto metadata, Kobo & KOReader sync, BookDrop imports, OPDS support, and a built-in reader for EPUB, PDF, and comics." \
      org.opencontainers.image.source="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.url="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.documentation="https://booklore.org/docs/getting-started" \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:21.0.9_10-jre-alpine"

RUN apk update && apk add nginx gettext su-exec

# Create default directories so the image can run without volume mappings
RUN mkdir -p /app/data /books /bookdrop

COPY ./nginx.conf /etc/nginx/nginx.conf
COPY --from=angular-build /angular-app/dist/booklore/browser /usr/share/nginx/html
COPY --from=springboot-build /springboot-app/build/libs/booklore-api-0.0.1-SNAPSHOT.jar /app/app.jar
COPY start.sh /start.sh
RUN chmod +x /start.sh

# Declare volumes for persistence (optional - image works without mapping these)
VOLUME ["/app/data", "/books", "/bookdrop"]

# Default port is 6060 (configurable via BOOKLORE_PORT env var)
# Internal Spring Boot runs on 8080, proxied by nginx
EXPOSE 6060

CMD ["/start.sh"]
