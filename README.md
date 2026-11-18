# ImageCloud 开发环境

主配置 `src/main/resources/application.yaml` 激活 `dev` profile。Spring Boot 从同目录的 `application-dev.yaml` 读取开发变量，填入主配置的 MySQL 和 Redis 占位符。

在 Git 忽略的 `src/main/resources/application-dev.yaml` 中填写 `imagecloud.mysql.username`、`imagecloud.mysql.password` 和 `imagecloud.redis.password`。MySQL 需要先准备 `imagecloud` 库和项目专用账号，Redis 使用共享实例的逻辑库 `1`。

开发环境使用本地 RSA 私钥签发登录令牌。在项目根目录生成一次 PKCS#8 PEM 私钥：

```sh
umask 077
mkdir -p dev-secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out dev-secrets/jwt-private.pem
```

在现有的 `imagecloud` 配置下设置 `auth.jwt.private-key-location` 为 `file:./dev-secrets/jwt-private.pem`，并按开发服务地址设置 `auth.jwt.issuer`。Git 忽略 `dev-secrets`。部署时将同一环境的私钥作为文件挂载，用 `IMAGECLOUD_JWT_PRIVATE_KEY_LOCATION` 配置文件位置。令牌签发者、受众和有效期分别由 `IMAGECLOUD_JWT_ISSUER`、`IMAGECLOUD_JWT_AUDIENCE` 和 `IMAGECLOUD_JWT_ACCESS_TOKEN_TTL` 配置。

开发环境使用以下地址直接连接服务器。

| 服务 | 开发环境地址 |
| --- | --- |
| MySQL | `jdbc:mysql://120.48.119.173:13306/imagecloud` |
| Redis | `120.48.119.173:16379` |

从项目根目录启动 Spring Boot。

```sh
./mvnw spring-boot:run
```

使用 IntelliJ IDEA 时，将 Spring Boot 运行配置的 Working directory 设为项目根目录。
