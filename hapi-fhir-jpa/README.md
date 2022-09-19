HAPI FHIR JPA
=========

### Custom changes for Titan-Him

To enable Quartz clustering support we had to update the class "BaseHapiScheduler" from the hapi-fhir-jpa library.

A summary of changes is as below;

**Class BaseHapiScheduler**

- init() method is updated to inject properties from file "quartz.properties" on the classpath when scheduler's name is 
"clustered".
- a new method setDatasourcePropertiesFromEnv() takes care of actual property injection.

<br>

#### Generate the jar

To generate the JAR, mvn clean install command can be used with /hapi-fhir-jpa folder.

<br>
<br>

#### Debug cheatsheet

**Generate sources jar**

A new plugin can be added to **hapi-fhir-jpa/pom.xml** to generate the sources. These sources can be used for debugging 
purposes.
This can be done as follows;

- add the following instruction to **hapi-fhir-jpa/pom.xml**;
   ```
   <build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-source-plugin</artifactId>
				<executions>
					<execution>
						<id>attach-sources</id>
						<goals>
							<goal>jar</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	 </build>
   ```

**Plugin maven-checkstyle-plugin could not be resolved error**

- The version of hapi-fhir-checkstyle needs to be updated to 6.0.1 in parent **pom.xml** as the original version is not 
available in maven central repo.
