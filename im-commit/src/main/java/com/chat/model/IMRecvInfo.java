package com.chat.model;

import lombok.Data;

import java.util.List;

/**
 * 接收消息体
 * @param <T>
 */
@Data
public class IMRecvInfo<T> {

    /**
     * 命令类型 IMCmdType
     */
    private Integer cmd;

    /**
     * 发送方
     */
    private IMUserInfo sender;

    /**
     * 接收方用户列表
     */
    List<IMUserInfo> receivers;

    /**
     * 是否需要回调发送结果
     */
    private Boolean sendResult;

    /**
     * 当前服务名（回调发送结果使用）
     */
    private String serviceName;
    /**
     * 推送消息体
     */
    private T data;
}


