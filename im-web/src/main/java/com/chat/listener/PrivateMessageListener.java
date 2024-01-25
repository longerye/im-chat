package com.chat.listener;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chat.annotation.IMListener;
import com.chat.entity.ChatPrivateMessage;
import com.chat.enums.IMListenerType;
import com.chat.enums.IMSendCode;
import com.chat.enums.MessageStatus;
import com.chat.model.IMSendResult;
import com.chat.service.IPrivateMessageService;
import com.chat.vo.PrivateMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@IMListener(type = IMListenerType.PRIVATE_MESSAGE)
public class PrivateMessageListener implements MessageListener<PrivateMessageVO> {
    @Lazy
    @Autowired
    private IPrivateMessageService privateMessageService;
    @Override
    public void process(List<IMSendResult<PrivateMessageVO>> results) {
        Set<Long> messageIds = new HashSet<>();
        for(IMSendResult<PrivateMessageVO> result : results){
            PrivateMessageVO messageInfo = result.getData();
            // 更新消息状态,这里只处理成功消息，失败的消息继续保持未读状态
            if (result.getCode().equals(IMSendCode.SUCCESS.code())) {
                messageIds.add(messageInfo.getId());
                log.info("消息送达，消息id:{}，发送者:{},接收者:{},终端:{}", messageInfo.getId(), result.getSender().getUserId(), result.getReceiver().getUserId(), result.getReceiver().getTerminal());
            }
        }
        // 批量修改状态
        if(CollUtil.isNotEmpty(messageIds)){
            UpdateWrapper<ChatPrivateMessage> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda().in(ChatPrivateMessage::getId, messageIds)
                    .eq(ChatPrivateMessage::getStatus, MessageStatus.UNSEND.code())
                    .set(ChatPrivateMessage::getStatus, MessageStatus.SENDED.code());
            privateMessageService.update(updateWrapper);
        }
    }
}
