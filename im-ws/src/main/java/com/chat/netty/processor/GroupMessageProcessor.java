package com.chat.netty.processor;

import cn.hutool.core.util.StrUtil;
import com.chat.contant.IMRedisKey;
import com.chat.enums.IMCmdType;
import com.chat.enums.IMSendCode;
import com.chat.model.IMRecvInfo;
import com.chat.model.IMSendInfo;
import com.chat.model.IMSendResult;
import com.chat.model.IMUserInfo;
import com.chat.netty.UserChannelCtxMap;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageProcessor extends AbstractMessageProcessor<IMRecvInfo> {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void process(IMRecvInfo recvInfo) {
        IMUserInfo sender = recvInfo.getSender();
        List<IMUserInfo> receivers = recvInfo.getReceivers();
        log.info("接收到群消息，发送者:{},接收用户数量:{}，内容:{}", sender.getUserId(), receivers.size(), recvInfo.getData());
        for (IMUserInfo receiver : receivers) {
            try {
                ChannelHandlerContext channelCtx = UserChannelCtxMap.getChannelCtx(receiver.getUserId(), receiver.getTerminal());
                if (channelCtx != null) {
                    // 推送消息到用户
                    IMSendInfo sendInfo = new IMSendInfo();
                    sendInfo.setCmd(IMCmdType.GROUP_MESSAGE.code());
                    sendInfo.setData(recvInfo.getData());
                    channelCtx.channel().writeAndFlush(sendInfo);
                    // 消息发送成功确认
                    sendResult(recvInfo, receiver, IMSendCode.SUCCESS);

                } else {
                    // 消息发送成功确认
                    sendResult(recvInfo, receiver, IMSendCode.NOT_FIND_CHANNEL);
                    log.error("未找到channel,发送者:{},接收id:{}，内容:{}", sender.getUserId(), receiver.getUserId(), recvInfo.getData());
                }
            } catch (Exception e) {
                // 消息发送失败确认
                sendResult(recvInfo, receiver, IMSendCode.UNKONW_ERROR);
                log.error("发送消息异常,发送者:{},接收id:{}，内容:{}", sender.getUserId(), receiver.getUserId(), recvInfo.getData());
            }
        }
    }


    private void sendResult(IMRecvInfo recvInfo, IMUserInfo receiver, IMSendCode sendCode) {
        if (recvInfo.getSendResult()) {
            IMSendResult result = new IMSendResult();
            result.setSender(recvInfo.getSender());
            result.setReceiver(receiver);
            result.setCode(sendCode.code());
            result.setData(recvInfo.getData());
            // 推送到结果队列
            String key = StrUtil.join(":", IMRedisKey.IM_RESULT_GROUP_QUEUE,recvInfo.getServiceName());
            redisTemplate.opsForList().rightPush(key, result);
        }
    }
}
