# LingoBot 启动指南

## 环境依赖

- Java 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 14+
- Redis 6+

***

## 1. PostgreSQL

### 安装（首次，若未安装）

```bash
sudo apt install postgresql-14        # 安装 PostgreSQL 14 服务端
sudo pg_createcluster 14 main --start # 初始化并启动数据库集群
```

### 启动服务

```bash
sudo systemctl start postgresql
sudo systemctl status postgresql
```

### 初始化数据库（首次）

```bash
source .env  # 加载 .env，使 $DB_USERNAME / $DB_PASSWORD 在当前 shell 生效
sudo -u postgres psql \
  --variable=DB_USERNAME="$DB_USERNAME" \
  --variable=DB_PASSWORD="$DB_PASSWORD"
```

进入 `postgres=#` 后执行：

```sql
CREATE USER :DB_USERNAME WITH PASSWORD :'DB_PASSWORD';       -- 创建数据库用户（:VAR 为标识符，:'VAR' 为字符串）
CREATE DATABASE lingobotdb OWNER :DB_USERNAME;               -- 创建数据库，指定刚才的用户为所有者
GRANT ALL PRIVILEGES ON DATABASE lingobotdb TO :DB_USERNAME; -- 授予该用户全部权限
\q
```

***

## 2. Redis

### 配置密码（首次）

```bash
sudo nano /etc/redis/redis.conf  # 找到 # requirepass foobared，改为：
# requirepass <REDIS_PASSWORD 的值>
```

保存后重启：

```bash
sudo systemctl restart redis-server
```

### 启动服务

```bash
sudo systemctl start redis-server
sudo systemctl status redis-server
```

### 验证

```bash
redis-cli ping
# 返回 PONG 即正常
```

***

## 3. 后端（Spring Boot）

### 启动

```bash
cd lingobot-backend && mvn clean package -DskipTests && java -jar target/lingobot-backend-1.0.0.jar
```

***

## 4. 前端（React + Vite）

```bash
cd lingobot-frontend
npm install      # 首次或依赖更新后运行
npm run dev
```

***

## 启动顺序

```
PostgreSQL → Redis → 后端 → 前端
```

