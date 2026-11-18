package com.blank.imagecloud.models.event;

import lombok.Value;

/** 访问令牌已撤销，协作模块可按令牌 ID 关闭关联连接。 */
@Value
public class AccessTokenRevokedEvent {
    String tokenId;
}
