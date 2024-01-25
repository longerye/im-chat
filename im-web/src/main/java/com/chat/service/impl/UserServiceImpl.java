package com.chat.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.config.JwtProperties;
import com.chat.dto.LoginDTO;
import com.chat.dto.ModifyPwdDTO;
import com.chat.dto.RegisterDTO;
import com.chat.entity.ChatFriend;
import com.chat.entity.ChatGroupMember;
import com.chat.entity.ChatUser;
import com.chat.enums.IMTerminalType;
import com.chat.enums.ResultCode;
import com.chat.exception.GlobalException;
import com.chat.mapper.UserMapper;
import com.chat.sender.IMRedisSender;
import com.chat.service.IFriendService;
import com.chat.service.IGroupMemberService;
import com.chat.service.IUserService;
import com.chat.session.SessionContext;
import com.chat.session.UserSession;
import com.chat.util.BeanUtils;
import com.chat.util.JwtUtil;
import com.chat.vo.LoginVO;
import com.chat.vo.OnlineTerminalVO;
import com.chat.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, ChatUser> implements IUserService {

    private final PasswordEncoder passwordEncoder;
    private final IGroupMemberService groupMemberService;
    private final IFriendService friendService;
    private final JwtProperties jwtProperties;
    private final IMRedisSender imRedisSender;

    @Override
    public LoginVO login(LoginDTO dto) {
        ChatUser user = this.findUserByUserName(dto.getUserName());
        if (null == user) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new GlobalException(ResultCode.PASSWOR_ERROR);
        }
        // 生成token
        UserSession session = BeanUtils.copyProperties(user, UserSession.class);
        session.setUserId(user.getId());
        session.setTerminal(dto.getTerminal());
        String strJson = JSON.toJSONString(session);
        String authorization = JwtUtil.sign(user.getId(), strJson, jwtProperties.getAuthorizationExpireIn(), jwtProperties.getAuthorizationSecret());
        String refreshToken = JwtUtil.sign(user.getId(), strJson, jwtProperties.getRefreshTokenExpireIn(), jwtProperties.getRefreshTokenSecret());
        LoginVO vo = new LoginVO();
        vo.setAuthorization(authorization);
        vo.setAuthorizationExpiresIn(jwtProperties.getAuthorizationExpireIn());
        vo.setRefreshToken(refreshToken);
        vo.setRefreshTokenExpiresIn(jwtProperties.getRefreshTokenExpireIn());
        return vo;
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        //验证 token
        if (!JwtUtil.checkSign(refreshToken, jwtProperties.getRefreshTokenSecret())) {
            throw new GlobalException("refreshToken无效或已过期");
        }
        String strJson = JwtUtil.getInfo(refreshToken);
        Long userId = JwtUtil.getUserId(refreshToken);
        String authorization = JwtUtil.sign(userId, strJson, jwtProperties.getAuthorizationExpireIn(), jwtProperties.getAuthorizationSecret());
        String newRefreshToken = JwtUtil.sign(userId, strJson, jwtProperties.getRefreshTokenExpireIn(), jwtProperties.getRefreshTokenSecret());
        LoginVO vo = new LoginVO();
        vo.setAuthorization(authorization);
        vo.setAuthorizationExpiresIn(jwtProperties.getAuthorizationExpireIn());
        vo.setRefreshToken(newRefreshToken);
        vo.setRefreshTokenExpiresIn(jwtProperties.getRefreshTokenExpireIn());
        return vo;
    }

    @Override
    public void register(RegisterDTO dto) {
        ChatUser user = this.findUserByUserName(dto.getUserName());
        if (null != user) {
            throw new GlobalException(ResultCode.USERNAME_ALREADY_REGISTER);
        }
        user = BeanUtils.copyProperties(dto, ChatUser.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        this.save(user);
        log.info("注册用户，用户id:{},用户名:{},昵称:{}", user.getId(), dto.getUserName(), dto.getNickName());
    }

    @Override
    public void modifyPassword(ModifyPwdDTO dto) {
        UserSession session = SessionContext.getSession();
        ChatUser user = this.getById(session.getUserId());
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new GlobalException("旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        this.updateById(user);
        log.info("用户修改密码，用户id:{},用户名:{},昵称:{}", user.getId(), user.getUserName(), user.getNickName());
    }

    @Override
    public ChatUser findUserByUserName(String username) {
        LambdaQueryWrapper<ChatUser> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(ChatUser::getUserName, username);
        return this.getOne(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UserVO vo) {
        UserSession session = SessionContext.getSession();
        if (!session.getUserId().equals(vo.getId())) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "不允许修改其他用户的信息!");
        }
        ChatUser user = this.getById(vo.getId());
        if (Objects.isNull(user)) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR, "用户不存在");
        }
        // 更新好友昵称和头像
        if (!user.getNickName().equals(vo.getNickName()) || !user.getHeadImageThumb().equals(vo.getHeadImageThumb())) {
            QueryWrapper<ChatFriend> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(ChatFriend::getFriendId, session.getUserId());
            List<ChatFriend> chatFriends = friendService.list(queryWrapper);
            for (ChatFriend chatFriend : chatFriends) {
                chatFriend.setFriendNickName(vo.getNickName());
                chatFriend.setFriendHeadImage(vo.getHeadImageThumb());
            }
            friendService.updateBatchById(chatFriends);
        }
        // 更新群聊中的头像
        if (!user.getHeadImageThumb().equals(vo.getHeadImageThumb())) {
            List<ChatGroupMember> members = groupMemberService.findByUserId(session.getUserId());
            for (ChatGroupMember member : members) {
                member.setHeadImage(vo.getHeadImageThumb());
            }
            groupMemberService.updateBatchById(members);
        }
        // 更新用户信息
        user.setNickName(vo.getNickName());
        user.setSex(vo.getSex());
        user.setSignature(vo.getSignature());
        user.setHeadImage(vo.getHeadImage());
        user.setHeadImageThumb(vo.getHeadImageThumb());
        this.updateById(user);
        log.info("用户信息更新，用户:{}}", user);
    }

    @Override
    public UserVO findUserById(Long id) {
        ChatUser user = this.getById(id);
        UserVO vo = BeanUtils.copyProperties(user, UserVO.class);
        vo.setOnline(imRedisSender.isOnline(id));
        return vo;
    }

    @Override
    public List<UserVO> findUserByName(String name) {
        LambdaQueryWrapper<ChatUser> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.like(ChatUser::getUserName, name).or().like(ChatUser::getNickName, name).last("limit 20");
        List<ChatUser> users = this.list(queryWrapper);
        List<Long> userIds = users.stream().map(ChatUser::getId).collect(Collectors.toList());
        List<Long> onlineUserIds = imRedisSender.getOnlineUser(userIds);
        return users.stream().map(u -> {
            UserVO vo = BeanUtils.copyProperties(u, UserVO.class);
            vo.setOnline(onlineUserIds.contains(u.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<OnlineTerminalVO> getOnlineTerminals(String userIds) {
        List<Long> userIdList = Arrays.stream(userIds.split(",")).map(Long::parseLong).collect(Collectors.toList());
        // 查询在线的终端
        Map<Long, List<IMTerminalType>> terminalMap = imRedisSender.getOnlineTerminal(userIdList);
        // 组装vo
        List<OnlineTerminalVO> vos = new LinkedList<>();
        terminalMap.forEach((userId, types) -> {
            List<Integer> terminals = types.stream().map(IMTerminalType::code).collect(Collectors.toList());
            vos.add(new OnlineTerminalVO(userId, terminals));
        });
        return vos;
    }
}
