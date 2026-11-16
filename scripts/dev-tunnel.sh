#!/bin/sh
# MySQL 和 Redis 发布在服务器的回环地址上，本地转发也仅绑定回环地址。
exec ssh -NT -p 22 \
    -o ExitOnForwardFailure=yes \
    -o ConnectTimeout=10 \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -L 127.0.0.1:13306:127.0.0.1:39306 \
    -L 127.0.0.1:16379:127.0.0.1:39379 \
    root@120.48.119.173
