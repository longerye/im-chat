package com.chat.sender;

import com.chat.contant.IMRedisKey;
import com.chat.enums.IMCmdType;
import com.chat.enums.IMListenerType;
import com.chat.enums.IMSendCode;
import com.chat.enums.IMTerminalType;
import com.chat.listener.MessageListenerMulticaster;
import com.chat.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Component
@Slf4j
public class IMRedisSenderImpl implements IMRedisSender{

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private final MessageListenerMulticaster multicast;


    @Value("${spring.application.name}")
    private String applicationName;

    public IMRedisSenderImpl(MessageListenerMulticaster multicast) {
        this.multicast = multicast;
    }

    @Override
    public<T> void sendPrivateMessage(IMPrivateMessage<T> message){
        List< IMSendResult> results = new LinkedList<>();
        for (Integer imTerminal : message.getImTerminals()) {
            // 获取对方连接的channelId
            String key = String.join(":", IMRedisKey.IM_USER_SERVER_ID, message.getReceiverId().toString(), imTerminal.toString());
            Integer serverId = (Integer)redisTemplate.opsForValue().get(key);
            if (serverId != null){
                //如何用户在线
                String sendKey = String.join(":", IMRedisKey.IM_MESSAGE_PRIVATE_QUEUE, serverId.toString());
                IMRecvInfo imRecvInfo = new IMRecvInfo();
                imRecvInfo.setCmd(IMCmdType.PRIVATE_MESSAGE.code());
                imRecvInfo.setSendResult(message.getSendResult());
                imRecvInfo.setSender(message.getSender());
                imRecvInfo.setServiceName(applicationName);
                imRecvInfo.setReceivers(Collections.singletonList(new IMUserInfo(message.getReceiverId(), imTerminal)));
                imRecvInfo.setData(message.getData());
                redisTemplate.opsForList().rightPush(sendKey, imRecvInfo);
            }else {
                //如果用户不在线
                IMSendResult result = new IMSendResult();
                result.setSender(message.getSender());
                result.setReceiver(new IMUserInfo(message.getReceiverId(), imTerminal));
                result.setCode(IMSendCode.NOT_ONLINE.code());
                result.setData(message.getData());
                results.add(result);
            }

        }

        // 对离线用户回复消息状态
        if(message.getSendResult() && !results.isEmpty()){
            multicast.multicast(IMListenerType.PRIVATE_MESSAGE, results);
        }
    }

    @Override
    public <T> void sendGroupMessage(IMGroupMessage<T> message) {

        Map<String, IMUserInfo> sendMap = new HashMap<>();
        message.getImTerminals().forEach(terCode->{
            message.getReceiverIds().forEach(receiverId->{
                String key = String.join(":", IMRedisKey.IM_USER_SERVER_ID, receiverId.toString(), terCode.toString());
                sendMap.put(key, new IMUserInfo(receiverId, terCode));
            });
        });
        if (sendMap.isEmpty()){
            return ;
        }

        List<Object> objects = redisTemplate.opsForValue().multiGet(sendMap.keySet());
        // 格式:map<服务器id,list<接收方>>
        Map<Integer, List<IMUserInfo>> serverMap = new HashMap<>();
        List<IMUserInfo> offLineUsers = new LinkedList<>();
        Iterator<Object> iterator = objects.iterator();
        for (Map.Entry<String, IMUserInfo> entry : sendMap.entrySet()) {
            Object next = iterator.next();
            if (next != null){
                List<IMUserInfo> imUserInfos = serverMap.computeIfAbsent((Integer) next, o -> new LinkedList<>());
                imUserInfos.add(entry.getValue());
            }
            else {
                // 加入离线列表
                offLineUsers.add(entry.getValue());
            }
        }

        // 逐个server发送
        for (Map.Entry<Integer, List<IMUserInfo>> entry : serverMap.entrySet()) {
            IMRecvInfo recvInfo = new IMRecvInfo();
            recvInfo.setCmd(IMCmdType.GROUP_MESSAGE.code());
            recvInfo.setReceivers(new LinkedList<>(entry.getValue()));
            recvInfo.setSender(message.getSender());
            recvInfo.setServiceName(applicationName);
            recvInfo.setSendResult(message.getSendResult());
            recvInfo.setData(message.getData());
            // 推送至队列
            String key = String.join(":", IMRedisKey.IM_MESSAGE_GROUP_QUEUE, entry.getKey().toString());
            redisTemplate.opsForList().rightPush(key, recvInfo);
        }

        // 推送给自己的其他终端
        if (message.getSendToSelf()) {
            for (Integer terminal : IMTerminalType.codes()) {
                if (terminal.equals(message.getSender().getTerminal())) {
                    continue;
                }
                // 获取终端连接的channelId
                String key = String.join(":", IMRedisKey.IM_USER_SERVER_ID, message.getSender().getUserId().toString(), terminal.toString());
                Integer serverId = (Integer)redisTemplate.opsForValue().get(key);
                // 如果终端在线，将数据存储至redis，等待拉取推送
                if (serverId != null) {
                    IMRecvInfo recvInfo = new IMRecvInfo();
                    recvInfo.setCmd(IMCmdType.GROUP_MESSAGE.code());
                    recvInfo.setSender(message.getSender());
                    recvInfo.setReceivers(Collections.singletonList(new IMUserInfo(message.getSender().getUserId(), terminal)));
                    // 自己的消息不需要回推消息结果
                    recvInfo.setSendResult(false);
                    recvInfo.setData(message.getData());
                    String sendKey = String.join(":", IMRedisKey.IM_MESSAGE_GROUP_QUEUE, serverId.toString());
                    redisTemplate.opsForList().rightPush(sendKey, recvInfo);
                }
            }
        }
        // 对离线用户回复消息状态
        if(message.getSendResult() && !offLineUsers.isEmpty()){
            List<IMSendResult> results = new LinkedList<>();
            for (IMUserInfo offLineUser : offLineUsers) {
                IMSendResult result = new IMSendResult();
                result.setSender(message.getSender());
                result.setReceiver(offLineUser);
                result.setCode(IMSendCode.NOT_ONLINE.code());
                result.setData(message.getData());
                results.add(result);
            }
            multicast.multicast(IMListenerType.GROUP_MESSAGE, results);
        }




    }

    @Override
    public Boolean isOnline(Long userId) {
        String key = String.join(":", IMRedisKey.IM_USER_SERVER_ID, userId.toString(), "*");
        return redisTemplate.hasKey(key);
    }

    @Override
    public List<Long> getOnlineUser(List<Long> userIds) {
        return new LinkedList<>(getOnlineTerminal(userIds).keySet());
    }

    @Override
    public Map<Long, List<IMTerminalType>> getOnlineTerminal(List<Long> userIds) {
        if (userIds.isEmpty()){
            return Collections.emptyMap();
        }

        // 把所有用户的key都存起来
        Map<String,IMUserInfo> userMap = new HashMap<>();
        userIds.forEach(userId->{
            IMTerminalType.codes().forEach(type->{
                String key = String.join(":", IMRedisKey.IM_USER_SERVER_ID, userId.toString(), type.toString());
                userMap.put(key, new IMUserInfo(userId, type));
            });
        });
        List<Object> onlineList = redisTemplate.opsForValue().multiGet(userMap.keySet());
        assert onlineList != null;
        Iterator<Object> iterator = onlineList.iterator();
        Map<Long,List<IMTerminalType>> onlineMap = new HashMap<>();
        for (Map.Entry<String, IMUserInfo> entry : userMap.entrySet()) {
            if (iterator.next() != null){
                IMUserInfo info = entry.getValue();
                List<IMTerminalType> imTerminalTypes = onlineMap.computeIfAbsent(info.getUserId(), o -> new LinkedList<>());
                imTerminalTypes.add(IMTerminalType.fromCode(info.getTerminal()));
            }
        }

        return onlineMap;
    }


}
