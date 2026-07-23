# 前端 Dockerfile — 多阶段构建
# Stage 1: Node 构建
FROM node:20-alpine AS builder
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@9 --activate

COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile

COPY . .
# 跳过 vue-tsc 类型检查，直接构建（Docker 环境类型定义可能与本地不同）
ARG APP_BUILD_ID=
RUN VITE_VERSION="${APP_BUILD_ID:-docker-$(date +%Y%m%d%H%M%S)}" \
    VITE_ACCESS_MODE=backend \
    VITE_API_URL=/ \
    VITE_BASE_URL=/ \
    npx vite build

# Stage 2: Nginx 运行
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
