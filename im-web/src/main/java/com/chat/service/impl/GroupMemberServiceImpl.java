package com.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.contant.RedisKey;
import com.chat.entity.ChatGroupMember;
import com.chat.mapper.GroupMemberMapper;
import com.chat.service.IGroupMemberService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@CacheConfig(cacheNames = RedisKey.IM_CACHE_GROUP_MEMBER_ID)
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, ChatGroupMember> implements IGroupMemberService {
    @CacheEvict(key = "#member.getGroupId()")
    @Override
    public boolean save(ChatGroupMember member) {
        return super.save(member);
    }

    @CacheEvict(key = "#groupId")
    @Override
    public boolean saveOrUpdateBatch(Long groupId, List<ChatGroupMember> members) {
        return super.saveOrUpdateBatch(members);
    }


    @Override
    public ChatGroupMember findByGroupAndUserId(Long groupId, Long userId) {
        QueryWrapper<ChatGroupMember> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(ChatGroupMember::getGroupId, groupId)
                .eq(ChatGroupMember::getUserId, userId);
        return this.getOne(wrapper);
    }

    @Override
    public List<ChatGroupMember> findByUserId(Long userId) {
        LambdaQueryWrapper<ChatGroupMember> memberWrapper = Wrappers.lambdaQuery();
        memberWrapper.eq(ChatGroupMember::getUserId, userId)
                .eq(ChatGroupMember::getQuit, false);
        return this.list(memberWrapper);
    }

    @Override
    public List<ChatGroupMember> findByGroupId(Long groupId) {
        LambdaQueryWrapper<ChatGroupMember> memberWrapper = Wrappers.lambdaQuery();
        memberWrapper.eq(ChatGroupMember::getGroupId, groupId);
        return this.list(memberWrapper);
    }

    @Cacheable(key = "#groupId")
    @Override
    public List<Long> findUserIdsByGroupId(Long groupId) {
        LambdaQueryWrapper<ChatGroupMember> memberWrapper = Wrappers.lambdaQuery();
        memberWrapper.eq(ChatGroupMember::getGroupId, groupId)
                .eq(ChatGroupMember::getQuit, false);
        List<ChatGroupMember> members = this.list(memberWrapper);
        return members.stream().map(ChatGroupMember::getUserId).collect(Collectors.toList());
    }

    @CacheEvict(key = "#groupId")
    @Override
    public void removeByGroupId(Long groupId) {
        LambdaUpdateWrapper<ChatGroupMember> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(ChatGroupMember::getGroupId, groupId)
                .set(ChatGroupMember::getQuit, true);
        this.update(wrapper);
    }

    @CacheEvict(key = "#groupId")
    @Override
    public void removeByGroupAndUserId(Long groupId, Long userId) {
        LambdaUpdateWrapper<ChatGroupMember> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(ChatGroupMember::getGroupId, groupId)
                .eq(ChatGroupMember::getUserId, userId)
                .set(ChatGroupMember::getQuit, true);
        this.update(wrapper);
    }
}
