FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 创建上传目录
RUN mkdir -p /data/uploads/schedule

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
