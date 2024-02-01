brief introduction:
    I have always had a social dream, I want to do an IM application, I have seen a lot of excellent
  open source projects, but there is no suitable for myself. So I used my break time to write such a system.
    IM-chat is a web version of chat software implemented in imitation of wechat, which is currently completely open source.
  Support private chat, group chat, offline message, send voice, pictures, files, emojis and other functions
  Video chat support (based on webrtc implementation, ssl certificate required) The backend uses springboot+netty,
  and the web uses vue Servers support cluster deployment, and each im-server processes only the messages of its own connected users
    Technology stack:
        Back-end frameworks: Springboot, Netty, Mybatis-plus, Swagger, Jwt
        Technical components: Mysql, Redis, Minio
        Front-end technology: Vue, Eelement-ui, Webrtc