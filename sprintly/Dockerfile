FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy the parent pom and all submodule poms first, to cache dependencies if no POM changes have occurred
COPY pom.xml .
COPY sprintly-common/pom.xml sprintly-common/
COPY sprintly-auth/pom.xml sprintly-auth/
COPY sprintly-user/pom.xml sprintly-user/
COPY sprintly-task/pom.xml sprintly-task/
COPY sprintly-notification/pom.xml sprintly-notification/
COPY sprintly-gateway/pom.xml sprintly-gateway/
COPY sprintly-cli/pom.xml sprintly-cli/

# Download all required dependencies into one layer
RUN mvn dependency:go-offline -B

# Copy all the source code
COPY . .

# Build the project
RUN mvn clean package -DskipTests -B

# -------------------------
# Gateway Runner Stage
# -------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /app/sprintly-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
