# 多阶段构建：先编译，再产出精简运行镜像（JDK 21 JRE）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# 先拉依赖（利用层缓存，业务代码改动不会重下依赖）
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8501
# 数据缓存目录（可挂载卷持久化）
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "app.jar"]
