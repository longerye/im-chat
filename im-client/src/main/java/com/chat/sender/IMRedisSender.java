package com.chat.sender;

import com.chat.enums.IMTerminalType;
import com.chat.model.IMGroupMessage;
import com.chat.model.IMPrivateMessage;

import java.util.List;
import java.util.Map;

public interface IMRedisSender {

    /**
     * 私聊消息
     * @param message
     * @param <T>
     */
    public<T> void sendPrivateMessage(IMPrivateMessage<T> message);

    /**
     * 群聊消息
     */
    public<T> void sendGroupMessage(IMGroupMessage<T> message);


    /**
     * 判断用户是否在线
     */
    public Boolean isOnline(Long userId);


    /**
     * 判断多个用户是否在线
     */
    public List<Long> getOnlineUser(List<Long> userIds);

    /**
     * 判断多个用户是否在线
     */
    public Map<Long,List<IMTerminalType>> getOnlineTerminal(List<Long> userIds);




}
