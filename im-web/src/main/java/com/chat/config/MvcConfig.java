package com.chat.config;

import com.chat.interceptor.AuthInterceptor;
import com.chat.interceptor.XssInterceptor;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@AllArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final XssInterceptor xssInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(xssInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PERMIT_URL);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用BCrypt加密密码
        return new BCryptPasswordEncoder();
    }

    /**
     * security 过滤地址
     */
    public static final String[] PERMIT_URL = {"/login", "/logout", "/register", "/refreshToken",
            "/swagger-resources/**", "/webjars/**", "/v2/**", "/swagger-ui/**","/swagger-ui.html","/swagger-resources/**","/doc.html","/static/**"};


}
