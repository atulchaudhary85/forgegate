FROM node:22.14.0-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

FROM nginx:1.27.4-alpine
RUN sed -i 's#pid */var/run/nginx.pid;#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf
RUN addgroup -S forgegate && adduser -S -G forgegate -u 10001 forgegate && mkdir -p /var/cache/nginx /var/run /tmp/nginx && chown -R forgegate:forgegate /var/cache/nginx /var/run /tmp/nginx /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=forgegate:forgegate /app/dist /usr/share/nginx/html
USER 10001:forgegate
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
