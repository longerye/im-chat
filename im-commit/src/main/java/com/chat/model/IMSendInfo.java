package com.chat.model;

import lombok.Data;

/**
 * 发送消息体
 * @param <T>
 */
@Data
public class IMSendInfo<T> {

    /**
     * 命令
     */
    private Integer cmd;

    /**
     * 推送消息体
     */
    private T data;

}
