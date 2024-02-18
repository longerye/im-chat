# IM-chat Application

IM-chat is a robust, web-based chat solution, designed to mimic popular instant messaging platforms such as WeChat. The project was conceived from a desire to create a truly personalized and open-source communication platform. Developed during my spare time, IM-chat is designed for adaptability and scalability to cater to varying user needs.

## Features:
IM-chat offers an extensive suite of features for comprehensive communication:

- **Private and Group Chat:** Initiate direct conversations or reach out to teams concurrently.

- **Offline Messaging:** Stay in the loop with offline messaging capabilities - no conversation missed.

- **Multimedia Messaging:** With support for voice, pictures, files, and emojis, conveying meaning beyond simple text is made easy.

- **Video Chat:** Hold face-to-face meetings with the secure video chat feature that is built using WebRTC technology. Please note, an SSL Certificate is required for this feature.

The backend has been robustly designed using Springboot and Netty, while the user interface is seamlessly brought to life using Vue. The enlightened architectural approach allows for deployment clustering, letting each im-server process messages specific to its connected users, ensuring optimal load management and performance efficiency.

## Technology Stack:
The application leverages a wide range of contemporary technologies:

- **Back-end frameworks:** Springboot forms the backbone of the server, aided by Netty for network communications. The system also implements supplementary libraries like Mybatis-plus, Swagger, and Jwt.

- **Databases & Storage:** Data is managed by the reliable and ubiquitous MySQL and Redis, while Minio serves as the storage solution handling all your file needs.

- **Front-end technology:** Vue provides the user-centric interface, Element-UI offers widget-based UI components, and WebRTC handles real-time communication.

The modular design and use of open-source tools make IM-chat easily configurable, scalable, and adaptable to cater to wide-ranging business and individual user needs. This application is a perfect starting point for anyone looking to build an advanced, feature-rich and secure messaging platform.
