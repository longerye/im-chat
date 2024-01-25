package com.chat.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class JwtProperties {

    @Value("${jwt.authorization.expireIn}")
    private Integer authorizationExpireIn;

    @Value("${jwt.authorization.secret}")
    private String authorizationSecret;

    @Value("${jwt.refreshToken.expireIn}")
    private Integer refreshTokenExpireIn;

    @Value("${jwt.refreshToken.secret}")
    private String refreshTokenSecret;
}
