package com.chat.task;

import com.alibaba.fastjson.JSONObject;
import com.chat.contant.IMRedisKey;
import com.chat.enums.IMCmdType;
import com.chat.model.IMRecvInfo;
import com.chat.netty.IMServerGroup;
import com.chat.netty.processor.AbstractMessageProcessor;
import com.chat.netty.processor.ProcessorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class PullGroupMessageTask extends AbstractPullMessageTask {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void pullMessage() {
        // 从redis拉取未读消息
        String key = String.join(":", IMRedisKey.IM_MESSAGE_GROUP_QUEUE, IMServerGroup.serverId + "");
        JSONObject jsonObject = (JSONObject) redisTemplate.opsForList().leftPop(key);
        while (!Objects.isNull(jsonObject)) {
            IMRecvInfo recvInfo = jsonObject.toJavaObject(IMRecvInfo.class);
            AbstractMessageProcessor processor = ProcessorFactory.createProcessor(IMCmdType.GROUP_MESSAGE);
            processor.process(recvInfo);
            // 下一条消息
            jsonObject = (JSONObject) redisTemplate.opsForList().leftPop(key);
        }
    }

}
