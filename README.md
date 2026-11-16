# ImageCloud 开发环境

主配置 `src/main/resources/application.yaml` 激活 `dev` profile。Spring Boot 从同目录的 `application-dev.yaml` 读取开发变量，填入主配置的 MySQL 和 Redis 占位符。

在 Git 忽略的 `src/main/resources/application-dev.yaml` 中填写 `imagecloud.mysql.username`、`imagecloud.mysql.password` 和 `imagecloud.redis.password`。MySQL 需要先准备 `imagecloud` 库和项目专用账号，Redis 使用共享实例的逻辑库 `1`。

先在一个终端启动 SSH 隧道，按提示输入服务器 SSH 密码，并保持该终端运行。使用 `Ctrl+C` 关闭隧道。

```sh
./scripts/dev-tunnel.sh
```

| 服务 | 本地地址 | 服务器监听地址 |
| --- | --- | --- |
| MySQL | `127.0.0.1:13306` | `127.0.0.1:39306` |
| Redis | `127.0.0.1:16379` | `127.0.0.1:39379` |

在另一个终端从项目根目录启动 Spring Boot。

```sh
./mvnw spring-boot:run
```

使用 IntelliJ IDEA 时，将 Spring Boot 运行配置的 Working directory 设为项目根目录。
