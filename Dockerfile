# ============================================================
# Stage 1: BUILD - compile the project and create the jar
# ============================================================

# Start from an image that has Maven and Java 17 (JDK) installed.
# "AS build" gives this stage a name so stage 2 can copy files from it.
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory inside the container. All later commands run here.
WORKDIR /app

# Copy ONLY pom.xml first.
# Docker caches each step, so the dependency download below is only repeated
# when pom.xml changes, not every time you edit your source code.
COPY pom.xml .

# ✅ CHANGED: the comment about skipping tests was moved from this line to the
#    "package" line below, because it describes -DskipTests, not this command.
#    Effect: the comments now match what each command really does.
# Download all dependencies ahead of time so they are stored in this cached layer.
RUN mvn -B dependency:go-offline

# Now copy the source code. This layer changes often, which is why it comes
# AFTER the dependency download.
COPY src ./src

# ✅ CHANGED: the comment about tests was added here, where it belongs.
# Tests already run in the "build" job of the GitHub workflow, so skip them here.
# "clean" removes old build output, "package" creates the executable jar in /app/target.
RUN mvn -B clean package -DskipTests

# ============================================================
# Stage 2: RUN - small image that only runs the application
# ============================================================

# Use a JRE (Java runtime only, no Maven or compiler), so the final image is smaller.
FROM eclipse-temurin:17-jre

# Working directory in the runtime image.
WORKDIR /app

# Copy the jar from the build stage and rename it to app.jar.
# "*.jar" matches only the executable jar; the ".jar.original" file is ignored.
COPY --from=build /app/target/*.jar app.jar

# Document that the application listens on port 8090.
# This must match server.port in application.properties.
# EXPOSE does not publish the port; you still need "-p 8090:8090" when running.
EXPOSE 8090

# The command that starts the application when the container starts.
ENTRYPOINT ["java", "-jar", "app.jar"]