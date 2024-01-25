package com.chat.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.contant.IMConstant;
import com.chat.contant.RedisKey;
import com.chat.dto.GroupMessageDTO;
import com.chat.entity.ChatGroup;
import com.chat.entity.ChatGroupMember;
import com.chat.entity.ChatGroupMessage;
import com.chat.enums.MessageStatus;
import com.chat.enums.MessageType;
import com.chat.enums.ResultCode;
import com.chat.exception.GlobalException;
import com.chat.mapper.GroupMessageMapper;
import com.chat.model.IMGroupMessage;
import com.chat.model.IMUserInfo;
import com.chat.sender.IMRedisSender;
import com.chat.service.IGroupMemberService;
import com.chat.service.IGroupMessageService;
import com.chat.service.IGroupService;
import com.chat.session.SessionContext;
import com.chat.session.UserSession;
import com.chat.util.BeanUtils;
import com.chat.util.SensitiveFilterUtil;
import com.chat.vo.GroupMessageVO;
import com.google.common.base.Splitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMessageServiceImpl extends ServiceImpl<GroupMessageMapper, ChatGroupMessage> implements IGroupMessageService {
    private final IGroupService groupService;
    private final IGroupMemberService groupMemberService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IMRedisSender imRedisSender;
    private final SensitiveFilterUtil sensitiveFilterUtil;

    @Override
    public Long sendMessage(GroupMessageDTO dto) {
        UserSession session = SessionContext.getSession();
        ChatGroup chatGroup = groupService.getById(dto.getGroupId());
        if (Objects.isNull(chatGroup)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "群聊不存在");
        }
        if (Boolean.TRUE.equals(chatGroup.getDeleted())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "群聊已解散");
        }
        // 是否在群聊里面
        ChatGroupMember member = groupMemberService.findByGroupAndUserId(dto.getGroupId(), session.getUserId());
        if (Objects.isNull(member) || Boolean.TRUE.equals(member.getQuit())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您已不在群聊里面，无法发送消息");
        }
        // 群聊成员列表
        List<Long> userIds = groupMemberService.findUserIdsByGroupId(chatGroup.getId());
        // 不用发给自己
        userIds = userIds.stream().filter(id -> !session.getUserId().equals(id)).collect(Collectors.toList());
        // 保存消息
        ChatGroupMessage msg = BeanUtils.copyProperties(dto, ChatGroupMessage.class);
        msg.setSendId(session.getUserId());
        msg.setSendTime(new Date());
        msg.setSendNickName(member.getAliasName());
        if (CollectionUtil.isNotEmpty(dto.getAtUserIds())) {
            msg.setAtUserIds(StrUtil.join(",", dto.getAtUserIds()));
        }
        this.save(msg);
        // 过滤消息内容
        String content = sensitiveFilterUtil.filter(dto.getContent());
        msg.setContent(content);
        // 群发
        GroupMessageVO msgInfo = BeanUtils.copyProperties(msg, GroupMessageVO.class);
        msgInfo.setAtUserIds(dto.getAtUserIds());
        IMGroupMessage<GroupMessageVO> sendMessage = new IMGroupMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverIds(userIds);
        sendMessage.setSendResult(false);
        sendMessage.setData(msgInfo);
        imRedisSender.sendGroupMessage(sendMessage);
        log.info("发送群聊消息，发送id:{},群聊id:{},内容:{}", session.getUserId(), dto.getGroupId(), dto.getContent());
        return msg.getId();
    }

    @Override
    public void recallMessage(Long id) {
        UserSession session = SessionContext.getSession();
        ChatGroupMessage msg = this.getById(id);
        if (Objects.isNull(msg)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "消息不存在");
        }
        if (!msg.getSendId().equals(session.getUserId())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "这条消息不是由您发送,无法撤回");
        }
        if (System.currentTimeMillis() - msg.getSendTime().getTime() > IMConstant.ALLOW_RECALL_SECOND * 1000) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "消息已发送超过5分钟，无法撤回");
        }
        // 判断是否在群里
        ChatGroupMember member = groupMemberService.findByGroupAndUserId(msg.getGroupId(), session.getUserId());
        if (Objects.isNull(member) || Boolean.TRUE.equals(member.getQuit())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您已不在群聊里面，无法撤回消息");
        }
        // 修改数据库
        msg.setStatus(MessageStatus.RECALL.code());
        this.updateById(msg);
        // 群发
        List<Long> userIds = groupMemberService.findUserIdsByGroupId(msg.getGroupId());
        // 不用发给自己
        userIds = userIds.stream().filter(uid -> !session.getUserId().equals(uid)).collect(Collectors.toList());
        GroupMessageVO msgInfo = BeanUtils.copyProperties(msg, GroupMessageVO.class);
        msgInfo.setType(MessageType.RECALL.code());
        String content = String.format("'%s'撤回了一条消息", member.getAliasName());
        msgInfo.setContent(content);
        msgInfo.setSendTime(new Date());

        IMGroupMessage<GroupMessageVO> sendMessage = new IMGroupMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverIds(userIds);
        sendMessage.setData(msgInfo);
        sendMessage.setSendResult(false);
        sendMessage.setSendToSelf(false);
        imRedisSender.sendGroupMessage(sendMessage);

        // 推给自己其他终端
        msgInfo.setContent("你撤回了一条消息");
        sendMessage.setSendToSelf(true);
        sendMessage.setReceiverIds(Collections.emptyList());
        sendMessage.setImTerminals(Collections.emptyList());
        imRedisSender.sendGroupMessage(sendMessage);
        log.info("撤回群聊消息，发送id:{},群聊id:{},内容:{}", session.getUserId(), msg.getGroupId(), msg.getContent());
    }


    @Override
    public List<GroupMessageVO> loadMessage(Long minId) {
        UserSession session = SessionContext.getSession();
        List<ChatGroupMember> members = groupMemberService.findByUserId(session.getUserId());
        if (CollectionUtil.isEmpty(members)) {
            return new ArrayList<>();
        }
        Map<Long, ChatGroupMember> groupMemberMap = CollStreamUtil.toIdentityMap(members, ChatGroupMember::getGroupId);
        Set<Long> ids = groupMemberMap.keySet();
        // 只能拉取最近1个月的
        Date minDate = DateUtils.addMonths(new Date(), -1);
        LambdaQueryWrapper<ChatGroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.gt(ChatGroupMessage::getId, minId).gt(ChatGroupMessage::getSendTime, minDate).in(ChatGroupMessage::getGroupId, ids)
                .ne(ChatGroupMessage::getStatus, MessageStatus.RECALL.code()).orderByAsc(ChatGroupMessage::getId).last("limit 100");

        List<ChatGroupMessage> messages = this.list(wrapper);
        // 转成vo
        List<GroupMessageVO> vos = messages.stream()
            .filter(m -> {
                //排除加群之前的消息
                ChatGroupMember member = groupMemberMap.get(m.getGroupId());
                return Objects.nonNull(member) && DateUtil.compare(member.getCreatedTime(), m.getSendTime()) <= 0;
            })
            .map(m -> {
                GroupMessageVO vo = BeanUtils.copyProperties(m, GroupMessageVO.class);
                // 被@用户列表
                if (StringUtils.isNotBlank(m.getAtUserIds()) && Objects.nonNull(vo)) {
                    List<String> atIds = Splitter.on(",").trimResults().splitToList(m.getAtUserIds());
                    vo.setAtUserIds(atIds.stream().map(Long::parseLong).collect(Collectors.toList()));
                }
                return vo;
            }).collect(Collectors.toList());
        // 消息状态,数据库没有存群聊的消息状态，需要从redis取
        List<String> keys = ids.stream().map(id -> String.join(":", RedisKey.IM_GROUP_READED_POSITION, id.toString(), session.getUserId().toString()))
                .collect(Collectors.toList());
        List<Object> sendPos = redisTemplate.opsForValue().multiGet(keys);
        int idx = 0;
        for (Long id : ids) {
            Object o = sendPos.get(idx);
            Integer sendMaxId = Objects.isNull(o) ? -1 : (Integer) o;
            vos.stream().filter(vo -> vo.getGroupId().equals(id)).forEach(vo -> {
                if (vo.getId() <= sendMaxId) {
                    // 已读
                    vo.setStatus(MessageStatus.READED.code());
                } else {
                    // 未推送
                    vo.setStatus(MessageStatus.UNSEND.code());
                }
            });
            idx++;
        }
        return vos;
    }

    @Override
    public void readedMessage(Long groupId) {
        UserSession session = SessionContext.getSession();
        // 取出最后的消息id
        LambdaQueryWrapper<ChatGroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ChatGroupMessage::getGroupId, groupId).orderByDesc(ChatGroupMessage::getId).last("limit 1").select(ChatGroupMessage::getId);
        ChatGroupMessage message = this.getOne(wrapper);
        if (Objects.isNull(message)) {
            return;
        }
        // 推送消息给自己的其他终端
        GroupMessageVO msgInfo = new GroupMessageVO();
        msgInfo.setType(MessageType.READED.code());
        msgInfo.setSendTime(new Date());
        msgInfo.setSendId(session.getUserId());
        msgInfo.setGroupId(groupId);
        IMGroupMessage<GroupMessageVO> sendMessage = new IMGroupMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setSendToSelf(true);
        sendMessage.setData(msgInfo);
        sendMessage.setSendResult(true);
        imRedisSender.sendGroupMessage(sendMessage);
        // 记录已读消息位置
        String key = StrUtil.join(":", RedisKey.IM_GROUP_READED_POSITION, groupId, session.getUserId());
        redisTemplate.opsForValue().set(key, message.getId());

    }

    @Override
    public List<GroupMessageVO> findHistoryMessage(Long groupId, Long page, Long size) {
        page = page > 0 ? page : 1;
        size = size > 0 ? size : 10;
        Long userId = SessionContext.getSession().getUserId();
        long stIdx = (page - 1) * size;
        // 群聊成员信息
        ChatGroupMember member = groupMemberService.findByGroupAndUserId(groupId, userId);
        if (Objects.isNull(member) || member.getQuit()) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您已不在群聊中");
        }
        // 查询聊天记录，只查询加入群聊时间之后的消息
        QueryWrapper<ChatGroupMessage> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(ChatGroupMessage::getGroupId, groupId).gt(ChatGroupMessage::getSendTime, member.getCreatedTime())
                .ne(ChatGroupMessage::getStatus, MessageStatus.RECALL.code()).orderByDesc(ChatGroupMessage::getId).last("limit " + stIdx + "," + size);

        List<ChatGroupMessage> messages = this.list(wrapper);
        List<GroupMessageVO> messageInfos =
                messages.stream().map(m -> BeanUtils.copyProperties(m, GroupMessageVO.class)).collect(Collectors.toList());
        log.info("拉取群聊记录，用户id:{},群聊id:{}，数量:{}", userId, groupId, messageInfos.size());
        return messageInfos;
    }

}
