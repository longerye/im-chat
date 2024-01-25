package com.chat.session;

import lombok.Data;

/**
 * webrtc 会话信息
 */
@Data
public class WebrtcSession {
    /**
     * 发起者id
     */
    private Long callerId;
    /**
     * 发起者终端类型
     */
    private Integer callerTerminal;

    /**
     * 接受者id
     */
    private Long acceptorId;

    /**
     * 接受者终端类型
     */
    private Integer acceptorTerminal;
}
