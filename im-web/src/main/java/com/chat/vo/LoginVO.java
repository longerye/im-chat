package com.chat.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("用户登录VO")
public class LoginVO {

    @ApiModelProperty(value = "每次请求都必须在header中携带authorization")
    private String authorization;

    @ApiModelProperty(value = "authorization过期时间(秒)")
    private Integer authorizationExpiresIn;

    @ApiModelProperty(value = "authorization过期后，通过refreshToken换取新的token")
    private String refreshToken;

    @ApiModelProperty(value = "refreshToken过期时间(秒)")
    private Integer refreshTokenExpiresIn;

}
