package com.chat.service.impl;


import com.chat.config.ICEServer;
import com.chat.config.ICEServerConfig;
import com.chat.contant.RedisKey;
import com.chat.enums.MessageType;
import com.chat.exception.GlobalException;
import com.chat.model.IMPrivateMessage;
import com.chat.model.IMUserInfo;
import com.chat.sender.IMRedisSender;
import com.chat.service.IWebrtcService;
import com.chat.session.SessionContext;
import com.chat.session.UserSession;
import com.chat.session.WebrtcSession;
import com.chat.vo.PrivateMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebrtcServiceImpl implements IWebrtcService {

    private final IMRedisSender imRedisSender;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ICEServerConfig iceServerConfig;

    @Override
    public void call(Long uid, String offer) {
        UserSession session = SessionContext.getSession();
        if (!imRedisSender.isOnline(uid)) {
            throw new GlobalException("对方目前不在线");
        }
        // 创建webrtc会话
        WebrtcSession webrtcSession = new WebrtcSession();
        webrtcSession.setCallerId(session.getUserId());
        webrtcSession.setCallerTerminal(session.getTerminal());
        String key = getSessionKey(session.getUserId(), uid);
        redisTemplate.opsForValue().set(key, webrtcSession, 12, TimeUnit.HOURS);
        // 向对方所有终端发起呼叫
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_CALL.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());
        messageInfo.setContent(offer);

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        sendMessage.setSendToSelf(false);
        sendMessage.setSendResult(false);
        sendMessage.setData(messageInfo);
        imRedisSender.sendPrivateMessage(sendMessage);

    }

    @Override
    public void accept(Long uid, @RequestBody String answer) {
        UserSession session = SessionContext.getSession();
        // 查询webrtc会话
        WebrtcSession webrtcSession = getWebrtcSession(session.getUserId(), uid);
        // 更新接受者信息
        webrtcSession.setAcceptorId(session.getUserId());
        webrtcSession.setAcceptorTerminal(session.getTerminal());
        String key = getSessionKey(session.getUserId(), uid);
        redisTemplate.opsForValue().set(key, webrtcSession, 12, TimeUnit.HOURS);
        // 向发起人推送接受通话信令
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_ACCEPT.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());
        messageInfo.setContent(answer);

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        // 告知其他终端已经接受会话,中止呼叫
        sendMessage.setSendToSelf(true);
        sendMessage.setSendResult(false);
        sendMessage.setImTerminals((Collections.singletonList(webrtcSession.getCallerTerminal())));
        sendMessage.setData(messageInfo);
        imRedisSender.sendPrivateMessage(sendMessage);
    }

    @Override
    public void reject(Long uid) {
        UserSession session = SessionContext.getSession();
        // 查询webrtc会话
        WebrtcSession webrtcSession = getWebrtcSession(session.getUserId(), uid);
        // 删除会话信息
        removeWebrtcSession(uid, session.getUserId());
        // 向发起人推送拒绝通话信令
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_REJECT.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        // 告知其他终端已经拒绝会话,中止呼叫
        sendMessage.setSendToSelf(true);
        sendMessage.setSendResult(false);
        sendMessage.setImTerminals(Collections.singletonList(webrtcSession.getCallerTerminal()));
        sendMessage.setData(messageInfo);
        imRedisSender.sendPrivateMessage(sendMessage);
    }

    @Override
    public void cancel(Long uid) {
        UserSession session = SessionContext.getSession();
        // 删除会话信息
        removeWebrtcSession(session.getUserId(), uid);
        // 向对方所有终端推送取消通话信令
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_ACCEPT.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        sendMessage.setSendToSelf(false);
        sendMessage.setSendResult(false);
        sendMessage.setData(messageInfo);
        // 通知对方取消会话
        imRedisSender.sendPrivateMessage(sendMessage);
    }

    @Override
    public void failed(Long uid, String reason) {
        UserSession session = SessionContext.getSession();
        // 查询webrtc会话
        WebrtcSession webrtcSession = getWebrtcSession(session.getUserId(), uid);
        // 删除会话信息
        removeWebrtcSession(uid, session.getUserId());
        // 向发起方推送通话失败信令
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_FAILED.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        // 告知其他终端已经会话失败,中止呼叫
        sendMessage.setSendToSelf(true);
        sendMessage.setSendResult(false);
        sendMessage.setImTerminals(Collections.singletonList(webrtcSession.getCallerTerminal()));
        sendMessage.setData(messageInfo);
        // 通知对方取消会话
        imRedisSender.sendPrivateMessage(sendMessage);

    }

    @Override
    public void leave(Long uid) {
        UserSession session = SessionContext.getSession();
        // 查询webrtc会话
        WebrtcSession webrtcSession = getWebrtcSession(session.getUserId(), uid);
        // 删除会话信息
        removeWebrtcSession(uid, session.getUserId());
        // 向对方推送挂断通话信令
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_HANDUP.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        sendMessage.setSendToSelf(false);
        sendMessage.setSendResult(false);
        Integer terminal = getTerminalType(uid, webrtcSession);
        sendMessage.setImTerminals(Collections.singletonList(terminal));
        sendMessage.setData(messageInfo);
        // 通知对方取消会话
        imRedisSender.sendPrivateMessage(sendMessage);
    }

    @Override
    public void candidate(Long uid, String candidate) {
        UserSession session = SessionContext.getSession();
        // 查询webrtc会话
        WebrtcSession webrtcSession = getWebrtcSession(session.getUserId(), uid);
        // 向发起方推送同步candidate信令
        PrivateMessageVO messageInfo = new PrivateMessageVO();
        messageInfo.setType(MessageType.RTC_CANDIDATE.code());
        messageInfo.setRecvId(uid);
        messageInfo.setSendId(session.getUserId());
        messageInfo.setContent(candidate);

        IMPrivateMessage<PrivateMessageVO> sendMessage = new IMPrivateMessage<>();
        sendMessage.setSender(new IMUserInfo(session.getUserId(), session.getTerminal()));
        sendMessage.setReceiverId(uid);
        sendMessage.setSendToSelf(false);
        sendMessage.setSendResult(false);
        Integer terminal = getTerminalType(uid, webrtcSession);
        sendMessage.setImTerminals(Collections.singletonList(terminal));
        sendMessage.setData(messageInfo);
        imRedisSender.sendPrivateMessage(sendMessage);
    }

    @Override
    public List<ICEServer> getIceServers() {
        return iceServerConfig.getIceServers();
    }

    private WebrtcSession getWebrtcSession(Long userId, Long uid) {
        String key = getSessionKey(userId, uid);
        WebrtcSession webrtcSession = (WebrtcSession) redisTemplate.opsForValue().get(key);
        if (webrtcSession == null) {
            throw new GlobalException("视频通话已结束");
        }
        return webrtcSession;
    }

    private void removeWebrtcSession(Long userId, Long uid) {
        String key = getSessionKey(userId, uid);
        redisTemplate.delete(key);
    }

    private String getSessionKey(Long id1, Long id2) {
        Long minId = id1 > id2 ? id2 : id1;
        Long maxId = id1 > id2 ? id1 : id2;
        return String.join(":", RedisKey.IM_WEBRTC_SESSION, minId.toString(), maxId.toString());
    }

    private Integer getTerminalType(Long uid, WebrtcSession webrtcSession) {
        if (uid.equals(webrtcSession.getCallerId())) {
            return webrtcSession.getCallerTerminal();
        }
        return webrtcSession.getAcceptorTerminal();
    }

}
