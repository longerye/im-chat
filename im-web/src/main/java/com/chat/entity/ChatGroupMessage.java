package com.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 群消息
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("chat_group_message")
public class ChatGroupMessage extends Model<ChatGroupMessage> {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群id
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 发送用户id
     */
    @TableField("send_id")
    private Long sendId;

    /**
     * 发送用户昵称
     */
    @TableField("send_nick_name")
    private String sendNickName;

    /**
     * @用户列表
     */
    @TableField("at_user_ids")
    private String atUserIds;
    /**
     * 发送内容
     */
    @TableField("content")
    private String content;

    /**
     * 消息类型 0:文字 1:图片 2:文件
     */
    @TableField("type")
    private Integer type;

    /**
     * 状态
     */
    @TableField("status")
    private Integer status;

    /**
     * 发送时间
     */
    @TableField("send_time")
    private Date sendTime;


    @Override
    public Serializable pkVal() {
        return this.id;
    }

}
