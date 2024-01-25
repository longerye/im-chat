package com.chat.listener;

import com.chat.model.IMSendResult;

import java.util.List;

public interface MessageListener<T> {

     void process(List<IMSendResult<T>> result);

}