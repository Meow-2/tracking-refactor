FROM 192.168.244.21/jdk/openjdk:8
WORKDIR /
COPY ./*.jar /app.jar

CMD ["java", "-Dfile.encoding=UTF-8", "-Djava.security.egd=file:/dev/.urandom", "-Duser.timezone=GMT+08", "-jar", "app.jar"]
EXPOSE 8080