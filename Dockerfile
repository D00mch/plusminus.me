FROM openjdk:8-alpine

COPY target/uberjar/plus-minus.jar /plus-minus/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/plus-minus/app.jar"]
