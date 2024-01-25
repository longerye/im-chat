package com.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.contant.IMConstant;
import com.chat.dto.PrivateMessageDTO;
import com.chat.entity.ChatFriend;
import com.chat.entity.ChatPrivateMessage;
import com.chat.enums.MessageStatus;
import com.chat.enums.MessageType;
import com.chat.enums.ResultCode;
import com.chat.exception.GlobalException;
import com.chat.mapper.PrivateMessageMapper;
import com.chat.model.IMPrivateMessage;
import com.chat.model.IMUserInfo;
import com.chat.sender.IMRedisSender;
import com.chat.service.IFriendService;
import com.chat.service.IPrivateMessageService;
import com.chat.session.SessionContext;
import com.chat.session.UserSession;
import com.chat.util.BeanUtils;
import com.chat.util.SensitiveFilterUtil;
import com.chat.vo.PrivateMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, ChatPrivateMessage> implements IPrivateMessageService {

    private final IFriendService friendService;
    private final IMRedisSender imRedisSender;
    private final SensitiveFilterUtil sensitiveFilterUtil;

    @Override
    public Long sendMessage(PrivateMessageDTO dto) {
        UserSession session = SessionContext.getSession();
        Boolean isFriends = friendService.isFriend(session.getUserId(), dto.getRecvId());
        if (Boolean.FALSE.equals(isFriends)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您已不是对方好友，无法发送消息");
        }
        // 保存消息
        ChatPrivateMessage msg = BeanUtils.copyProperties(dto, ChatPrivateMessage.class);
        msg.setSendId(session.getUserId());
        msg.setStatus(MessageStatus.UNSEND.code());
        msg.setSendTime(new Date());
        this.save(msg);
        // 过滤消息内容
        String content = sensitiveFilterUtil.filter(dto.getContent());
        msg.setContent(content);
        // 推送消息
        PrivateMessageVO msgInfo = BeanUtils.copyProperties(msg, PrivateMessageVO.class);
        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(msgInfo.getRecvId());
        sendMessage.setSendToSelf(true);
        sendMessage.setData(msgInfo);
        sendMessage.setSendResult(true);
        imRedisSender.sendPrivateMessage(sendMessage);
        log.info("发送私聊消息，发送id:{},接收id:{}，内容:{}", session.getUserId(), dto.getRecvId(), dto.getContent());
        return msg.getId();
    }

    @Override
    public void recallMessage(Long id) {
        UserSession session = SessionContext.getSession();
        ChatPrivateMessage msg = this.getById(id);
        if (Objects.isNull(msg)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "消息不存在");
        }
        if (!msg.getSendId().equals(session.getUserId())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "这条消息不是由您发送,无法撤回");
        }
        if (System.currentTimeMillis() - msg.getSendTime().getTime() > IMConstant.ALLOW_RECALL_SECOND * 1000) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "消息已发送超过5分钟，无法撤回");
        }
        // 修改消息状态
        msg.setStatus(MessageStatus.RECALL.code());
        this.updateById(msg);
        // 推送消息
        PrivateMessageVO msgInfo = BeanUtils.copyProperties(msg, PrivateMessageVO.class);
        msgInfo.setType(MessageType.RECALL.code());
        msgInfo.setSendTime(new Date());
        msgInfo.setContent("对方撤回了一条消息");

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(msgInfo.getRecvId());
        sendMessage.setSendToSelf(false);
        sendMessage.setData(msgInfo);
        sendMessage.setSendResult(false);
        imRedisSender.sendPrivateMessage(sendMessage);

        // 推给自己其他终端
        msgInfo.setContent("你撤回了一条消息");
        sendMessage.setSendToSelf(true);
        sendMessage.setImTerminals(Collections.emptyList());
        imRedisSender.sendPrivateMessage(sendMessage);
        log.info("撤回私聊消息，发送id:{},接收id:{}，内容:{}", msg.getSendId(), msg.getRecvId(), msg.getContent());
    }


    @Override
    public List<PrivateMessageVO> findHistoryMessage(Long friendId, Long page, Long size) {
        page = page > 0 ? page : 1;
        size = size > 0 ? size : 10;
        Long userId = SessionContext.getSession().getUserId();
        long stIdx = (page - 1) * size;
        QueryWrapper<ChatPrivateMessage> wrapper = new QueryWrapper<>();
        wrapper.lambda().and(wrap -> wrap.and(
                wp -> wp.eq(ChatPrivateMessage::getSendId, userId)
                        .eq(ChatPrivateMessage::getRecvId, friendId))
                .or(wp -> wp.eq(ChatPrivateMessage::getRecvId, userId)
                        .eq(ChatPrivateMessage::getSendId, friendId)))
                .ne(ChatPrivateMessage::getStatus, MessageStatus.RECALL.code())
                .orderByDesc(ChatPrivateMessage::getId)
                .last("limit " + stIdx + "," + size);

        List<ChatPrivateMessage> messages = this.list(wrapper);
        List<PrivateMessageVO> messageInfos = messages.stream().map(m -> BeanUtils.copyProperties(m, PrivateMessageVO.class)).collect(Collectors.toList());
        log.info("拉取聊天记录，用户id:{},好友id:{}，数量:{}", userId, friendId, messageInfos.size());
        return messageInfos;
    }


    @Override
    public List<PrivateMessageVO> loadMessage(Long minId) {
        UserSession session = SessionContext.getSession();
        List<ChatFriend> chatFriends = friendService.findFriendByUserId(session.getUserId());
        if (chatFriends.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> friendIds = chatFriends.stream().map(ChatFriend::getFriendId).collect(Collectors.toList());
        // 获取当前用户的消息
        LambdaQueryWrapper<ChatPrivateMessage> queryWrapper = Wrappers.lambdaQuery();
        // 只能拉取最近1个月的
        Date minDate = DateUtils.addMonths(new Date(), -1);
        queryWrapper.gt(ChatPrivateMessage::getId, minId)
                .ge(ChatPrivateMessage::getSendTime, minDate)
                .ne(ChatPrivateMessage::getStatus, MessageStatus.RECALL.code())
                .and(wrap -> wrap.and(
                        wp -> wp.eq(ChatPrivateMessage::getSendId, session.getUserId())
                                .in(ChatPrivateMessage::getRecvId, friendIds))
                        .or(wp -> wp.eq(ChatPrivateMessage::getRecvId, session.getUserId())
                                .in(ChatPrivateMessage::getSendId, friendIds)))
                .orderByAsc(ChatPrivateMessage::getId)
                .last("limit 100");

        List<ChatPrivateMessage> messages = this.list(queryWrapper);
        // 更新发送状态
        List<Long> ids = messages.stream()
                .filter(m -> !m.getSendId().equals(session.getUserId()) && m.getStatus().equals(MessageStatus.UNSEND.code()))
                .map(ChatPrivateMessage::getId)
                .collect(Collectors.toList());
        if (!ids.isEmpty()) {
            LambdaUpdateWrapper<ChatPrivateMessage> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.in(ChatPrivateMessage::getId, ids)
                    .set(ChatPrivateMessage::getStatus, MessageStatus.SENDED.code());
            this.update(updateWrapper);
        }
        log.info("拉取消息，用户id:{},数量:{}", session.getUserId(), messages.size());
        return messages.stream().map(m -> BeanUtils.copyProperties(m, PrivateMessageVO.class)).collect(Collectors.toList());
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void readedMessage(Long friendId) {
        UserSession session = SessionContext.getSession();
        // 推送消息
        PrivateMessageVO msgInfo = new PrivateMessageVO();
        msgInfo.setType(MessageType.READED.code());
        msgInfo.setSendTime(new Date());
        msgInfo.setSendId(session.getUserId());
        msgInfo.setRecvId(friendId);
        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(friendId);
        sendMessage.setSendToSelf(true);
        sendMessage.setData(msgInfo);
        sendMessage.setSendResult(false);
        imRedisSender.sendPrivateMessage(sendMessage);
        // 修改消息状态为已读
        LambdaUpdateWrapper<ChatPrivateMessage> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(ChatPrivateMessage::getSendId, friendId)
                .eq(ChatPrivateMessage::getRecvId, session.getUserId())
                .eq(ChatPrivateMessage::getStatus, MessageStatus.SENDED.code())
                .set(ChatPrivateMessage::getStatus, MessageStatus.READED.code());
        this.update(updateWrapper);
        log.info("消息已读，接收方id:{},发送方id:{}", session.getUserId(), friendId);
    }


    @Override
    public Long getMaxReadedId(Long friendId) {
        UserSession session = SessionContext.getSession();
        LambdaQueryWrapper<ChatPrivateMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ChatPrivateMessage::getSendId, session.getUserId())
                .eq(ChatPrivateMessage::getRecvId, friendId)
                .eq(ChatPrivateMessage::getStatus, MessageStatus.READED.code())
                .orderByDesc(ChatPrivateMessage::getId)
                .select(ChatPrivateMessage::getId)
                .last("limit 1");
        ChatPrivateMessage message = this.getOne(wrapper);
        if(Objects.isNull(message)){
            return -1L;
        }
        return message.getId();
    }
}
