package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IMUserInfo {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 用户终端类型 IMTerminalType
     */
    private Integer terminal;


}
