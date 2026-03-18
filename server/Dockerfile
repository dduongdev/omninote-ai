# syntax=docker/dockerfile:1
# Dùng Image gốc dựa trên Debian/Ubuntu (không dùng alpine) cho bước Build
# để tránh các lỗi DNS "Name has no usable address" khi Maven resolve repo.
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Ưu tiên copy file pom.xml trước để tận dụng Docker Layer Cache
COPY pom.xml .

# Sử dụng cơ chế BuildKit Mount Cache của Docker cho folder ~/.m2
# Maven Dependency sẽ chỉ tải lại những JAR bị thêm/thay đổi thay vì cào lại toàn bộ
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -T 1C

# Copy mã nguồn vào và build sau
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B -T 1C

# Bước Runtime dùng Alpine để có image nhỏ nhất
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

