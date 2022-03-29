

mvn io.quarkus.platform:quarkus-maven-plugin:2.7.5.Final:create \
    -DprojectGroupId=com.redhat \
    -DprojectArtifactId=backend-gelf \
    -Dextensions="resteasy,logging-gelf" \
    -DnoCode


mvn io.quarkus.platform:quarkus-maven-plugin:2.7.5.Final:create \
    -DprojectGroupId=com.redhat \
    -DprojectArtifactId=backend-micrometer \
    -Dextensions="resteasy,micrometer-registry-prometheus" \
    -DnoCode