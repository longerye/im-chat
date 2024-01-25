package com.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.contant.Constant;
import com.chat.contant.RedisKey;
import com.chat.entity.ChatFriend;
import com.chat.entity.ChatGroup;
import com.chat.entity.ChatGroupMember;
import com.chat.entity.ChatUser;
import com.chat.enums.ResultCode;
import com.chat.exception.GlobalException;
import com.chat.mapper.GroupMapper;
import com.chat.sender.IMRedisSender;
import com.chat.service.IFriendService;
import com.chat.service.IGroupMemberService;
import com.chat.service.IGroupService;
import com.chat.service.IUserService;
import com.chat.session.SessionContext;
import com.chat.session.UserSession;
import com.chat.util.BeanUtils;
import com.chat.vo.GroupInviteVO;
import com.chat.vo.GroupMemberVO;
import com.chat.vo.GroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@CacheConfig(cacheNames = RedisKey.IM_CACHE_GROUP)
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, ChatGroup> implements IGroupService {
    private final IUserService userService;
    private final IGroupMemberService groupMemberService;
    private final IFriendService friendsService;
    private final IMRedisSender imRedisSender;

    @Override
    public GroupVO createGroup(GroupVO vo) {
        UserSession session = SessionContext.getSession();
        ChatUser user = userService.getById(session.getUserId());
        // 保存群组数据
        ChatGroup chatGroup = BeanUtils.copyProperties(vo, ChatGroup.class);
        chatGroup.setOwnerId(user.getId());
        this.save(chatGroup);
        // 把群主加入群
        ChatGroupMember chatGroupMember = new ChatGroupMember();
        chatGroupMember.setGroupId(chatGroup.getId());
        chatGroupMember.setUserId(user.getId());
        chatGroupMember.setHeadImage(user.getHeadImageThumb());
        chatGroupMember.setAliasName(StringUtils.isEmpty(vo.getAliasName()) ? session.getNickName() : vo.getAliasName());
        chatGroupMember.setRemark(StringUtils.isEmpty(vo.getRemark()) ? chatGroup.getName() : vo.getRemark());
        groupMemberService.save(chatGroupMember);

        vo.setId(chatGroup.getId());
        vo.setAliasName(chatGroupMember.getAliasName());
        vo.setRemark(chatGroupMember.getRemark());
        log.info("创建群聊，群聊id:{},群聊名称:{}", chatGroup.getId(), chatGroup.getName());
        return vo;
    }

    @CacheEvict(value = "#vo.getId()")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public GroupVO modifyGroup(GroupVO vo) {
        UserSession session = SessionContext.getSession();
        // 校验是不是群主，只有群主能改信息
        ChatGroup chatGroup = this.getById(vo.getId());
        // 群主有权修改群基本信息
        if (chatGroup.getOwnerId().equals(session.getUserId())) {
            chatGroup = BeanUtils.copyProperties(vo, ChatGroup.class);
            this.updateById(chatGroup);
        }
        // 更新成员信息
        ChatGroupMember member = groupMemberService.findByGroupAndUserId(vo.getId(), session.getUserId());
        if (member == null) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您不是群聊的成员");
        }
        member.setAliasName(StringUtils.isEmpty(vo.getAliasName()) ? session.getNickName() : vo.getAliasName());
        member.setRemark(StringUtils.isEmpty(vo.getRemark()) ? Objects.requireNonNull(chatGroup).getName() : vo.getRemark());
        groupMemberService.updateById(member);
        log.info("修改群聊，群聊id:{},群聊名称:{}", chatGroup.getId(), chatGroup.getName());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "#groupId")
    @Override
    public void deleteGroup(Long groupId) {
        UserSession session = SessionContext.getSession();
        ChatGroup chatGroup = this.getById(groupId);
        if (!chatGroup.getOwnerId().equals(session.getUserId())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "只有群主才有权限解除群聊");
        }
        // 逻辑删除群数据
        chatGroup.setDeleted(true);
        this.updateById(chatGroup);
        // 删除成员数据
        groupMemberService.removeByGroupId(groupId);
        log.info("删除群聊，群聊id:{},群聊名称:{}", chatGroup.getId(), chatGroup.getName());
    }

    @Override
    public void quitGroup(Long groupId) {
        Long userId = SessionContext.getSession().getUserId();
        ChatGroup chatGroup = this.getById(groupId);
        if (chatGroup.getOwnerId().equals(userId)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您是群主，不可退出群聊");
        }
        // 删除群聊成员
        groupMemberService.removeByGroupAndUserId(groupId, userId);
        log.info("退出群聊，群聊id:{},群聊名称:{},用户id:{}", chatGroup.getId(), chatGroup.getName(), userId);
    }

    @Override
    public void kickGroup(Long groupId, Long userId) {
        UserSession session = SessionContext.getSession();
        ChatGroup chatGroup = this.getById(groupId);
        if (!chatGroup.getOwnerId().equals(session.getUserId())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您不是群主，没有权限踢人");
        }
        if (userId.equals(session.getUserId())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "亲，不能自己踢自己哟");
        }
        // 删除群聊成员
        groupMemberService.removeByGroupAndUserId(groupId, userId);
        log.info("踢出群聊，群聊id:{},群聊名称:{},用户id:{}", chatGroup.getId(), chatGroup.getName(), userId);
    }

    @Override
    public GroupVO findById(Long groupId) {
        UserSession session = SessionContext.getSession();
        ChatGroup chatGroup = this.getById(groupId);
        ChatGroupMember member = groupMemberService.findByGroupAndUserId(groupId, session.getUserId());
        if (member == null) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "您未加入群聊");
        }
        GroupVO vo = BeanUtils.copyProperties(chatGroup, GroupVO.class);
        vo.setAliasName(member.getAliasName());
        vo.setRemark(member.getRemark());
        return vo;
    }

    @Cacheable(value = "#groupId")
    @Override
    public ChatGroup getById(Long groupId) {
        ChatGroup chatGroup = super.getById(groupId);
        if (chatGroup == null) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "群组不存在");
        }
        if (chatGroup.getDeleted()) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "群组'" + chatGroup.getName() + "'已解散");
        }
        return chatGroup;
    }

    @Override
    public List<GroupVO> findGroups() {
        UserSession session = SessionContext.getSession();
        // 查询当前用户的群id列表
        List<ChatGroupMember> chatGroupMembers = groupMemberService.findByUserId(session.getUserId());
        if (chatGroupMembers.isEmpty()) {
            return new LinkedList<>();
        }
        // 拉取群列表
        List<Long> ids = chatGroupMembers.stream().map((ChatGroupMember::getGroupId)).collect(Collectors.toList());
        LambdaQueryWrapper<ChatGroup> groupWrapper = Wrappers.lambdaQuery();
        groupWrapper.in(ChatGroup::getId, ids);
        List<ChatGroup> chatGroups = this.list(groupWrapper);
        // 转vo
        return chatGroups.stream().map(g -> {
            GroupVO vo = BeanUtils.copyProperties(g, GroupVO.class);
            ChatGroupMember member = chatGroupMembers.stream().filter(m -> g.getId().equals(m.getGroupId())).findFirst().get();
            vo.setAliasName(member.getAliasName());
            vo.setRemark(member.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void invite(GroupInviteVO vo) {
        UserSession session = SessionContext.getSession();
        ChatGroup chatGroup = this.getById(vo.getGroupId());
        if (chatGroup == null) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "群聊不存在");
        }
        // 群聊人数校验
        List<ChatGroupMember> members = groupMemberService.findByGroupId(vo.getGroupId());
        long size = members.stream().filter(m -> !m.getQuit()).count();
        if (vo.getFriendIds().size() + size > Constant.MAX_GROUP_MEMBER) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "群聊人数不能大于" + Constant.MAX_GROUP_MEMBER + "人");
        }
        // 找出好友信息
        List<ChatFriend> chatFriends = friendsService.findFriendByUserId(session.getUserId());
        List<ChatFriend> friendsList = vo.getFriendIds().stream().map(id -> chatFriends.stream().filter(f -> f.getFriendId().equals(id)).findFirst().get())
                .collect(Collectors.toList());
        if (friendsList.size() != vo.getFriendIds().size()) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "部分用户不是您的好友，邀请失败");
        }
        // 批量保存成员数据
        List<ChatGroupMember> chatGroupMembers = friendsList.stream().map(f -> {
            Optional<ChatGroupMember> optional = members.stream().filter(m -> m.getUserId().equals(f.getFriendId())).findFirst();
            ChatGroupMember chatGroupMember = optional.orElseGet(ChatGroupMember::new);
            chatGroupMember.setGroupId(vo.getGroupId());
            chatGroupMember.setUserId(f.getFriendId());
            chatGroupMember.setAliasName(f.getFriendNickName());
            chatGroupMember.setRemark(chatGroup.getName());
            chatGroupMember.setHeadImage(f.getFriendHeadImage());
            chatGroupMember.setCreatedTime(new Date());
            chatGroupMember.setQuit(false);
            return chatGroupMember;
        }).collect(Collectors.toList());
        if (!chatGroupMembers.isEmpty()) {
            groupMemberService.saveOrUpdateBatch(chatGroup.getId(), chatGroupMembers);
        }
        log.info("邀请进入群聊，群聊id:{},群聊名称:{},被邀请用户id:{}", chatGroup.getId(), chatGroup.getName(), vo.getFriendIds());
    }

    @Override
    public List<GroupMemberVO> findGroupMembers(Long groupId) {
        List<ChatGroupMember> members = groupMemberService.findByGroupId(groupId);
        List<Long> userIds = members.stream().map(ChatGroupMember::getUserId).collect(Collectors.toList());
        List<Long> onlineUserIds = imRedisSender.getOnlineUser(userIds);
        return members.stream().map(m -> {
            GroupMemberVO vo = BeanUtils.copyProperties(m, GroupMemberVO.class);
            vo.setOnline(onlineUserIds.contains(m.getUserId()));
            return vo;
        }).sorted((m1, m2) -> m2.getOnline().compareTo(m1.getOnline())).collect(Collectors.toList());
    }

}
