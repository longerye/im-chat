package com.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.contant.RedisKey;
import com.chat.entity.ChatFriend;
import com.chat.entity.ChatUser;
import com.chat.enums.ResultCode;
import com.chat.exception.GlobalException;
import com.chat.mapper.FriendMapper;
import com.chat.mapper.UserMapper;
import com.chat.service.IFriendService;
import com.chat.session.SessionContext;
import com.chat.session.UserSession;
import com.chat.vo.FriendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = RedisKey.IM_CACHE_FRIEND)
public class FriendServiceImpl extends ServiceImpl<FriendMapper, ChatFriend> implements IFriendService {

    private final UserMapper userMapper;

    @Override
    public List<ChatFriend> findFriendByUserId(Long userId) {
        LambdaQueryWrapper<ChatFriend> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(ChatFriend::getUserId, userId);
        return this.list(queryWrapper);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addFriend(Long friendId) {
        long userId = SessionContext.getSession().getUserId();
        if (userId == friendId) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "不允许添加自己为好友");
        }
        // 互相绑定好友关系
        FriendServiceImpl proxy = (FriendServiceImpl) AopContext.currentProxy();
        proxy.bindFriend(userId, friendId);
        proxy.bindFriend(friendId, userId);
        log.info("添加好友，用户id:{},好友id:{}", userId, friendId);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delFriend(Long friendId) {
        long userId = SessionContext.getSession().getUserId();
        // 互相解除好友关系，走代理清理缓存
        FriendServiceImpl proxy = (FriendServiceImpl) AopContext.currentProxy();
        proxy.unbindFriend(userId, friendId);
        proxy.unbindFriend(friendId, userId);
        log.info("删除好友，用户id:{},好友id:{}", userId, friendId);
    }


    @Cacheable(key = "#userId1+':'+#userId2")
    @Override
    public Boolean isFriend(Long userId1, Long userId2) {
        QueryWrapper<ChatFriend> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ChatFriend::getUserId, userId1)
                .eq(ChatFriend::getFriendId, userId2);
        return this.count(queryWrapper) > 0;
    }


    @Override
    public void update(FriendVO vo) {
        long userId = SessionContext.getSession().getUserId();
        QueryWrapper<ChatFriend> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ChatFriend::getUserId, userId)
                .eq(ChatFriend::getFriendId, vo.getId());

        ChatFriend f = this.getOne(queryWrapper);
        if (f == null) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "对方不是您的好友");
        }

        f.setFriendHeadImage(vo.getHeadImage());
        f.setFriendNickName(vo.getNickName());
        this.updateById(f);
    }


    /**
     * 单向绑定好友关系
     *
     * @param userId   用户id
     * @param friendId 好友的用户id
     */
    @CacheEvict(key = "#userId+':'+#friendId")
    public void bindFriend(Long userId, Long friendId) {
        QueryWrapper<ChatFriend> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ChatFriend::getUserId, userId)
                .eq(ChatFriend::getFriendId, friendId);
        if (this.count(queryWrapper) == 0) {
            ChatFriend chatFriend = new ChatFriend();
            chatFriend.setUserId(userId);
            chatFriend.setFriendId(friendId);
            ChatUser friendInfo = userMapper.selectById(friendId);
            chatFriend.setFriendHeadImage(friendInfo.getHeadImage());
            chatFriend.setFriendNickName(friendInfo.getNickName());
            this.save(chatFriend);
        }
    }


    /**
     * 单向解除好友关系
     *
     * @param userId   用户id
     * @param friendId 好友的用户id
     */
    @CacheEvict(key = "#userId+':'+#friendId")
    public void unbindFriend(Long userId, Long friendId) {
        QueryWrapper<ChatFriend> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ChatFriend::getUserId, userId)
                .eq(ChatFriend::getFriendId, friendId);
        List<ChatFriend> chatFriends = this.list(queryWrapper);
        chatFriends.forEach(friend -> this.removeById(friend.getId()));
    }


    @Override
    public FriendVO findFriend(Long friendId) {
        UserSession session = SessionContext.getSession();
        QueryWrapper<ChatFriend> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ChatFriend::getUserId, session.getUserId())
                .eq(ChatFriend::getFriendId, friendId);
        ChatFriend chatFriend = this.getOne(wrapper);
        if (chatFriend == null) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "对方不是您的好友");
        }
        FriendVO vo = new FriendVO();
        vo.setId(chatFriend.getFriendId());
        vo.setHeadImage(chatFriend.getFriendHeadImage());
        vo.setNickName(chatFriend.getFriendNickName());
        return vo;
    }
}
