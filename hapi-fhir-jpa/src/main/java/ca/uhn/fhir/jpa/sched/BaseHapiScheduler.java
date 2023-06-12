package ca.uhn.fhir.jpa.sched;

/*-
 * #%L
 * hapi-fhir-jpa
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.model.sched.IHapiScheduler;
import ca.uhn.fhir.jpa.model.sched.ScheduledJobDefinition;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class BaseHapiScheduler implements IHapiScheduler {
	private static final Logger ourLog = LoggerFactory.getLogger(BaseHapiScheduler.class);

	private static final AtomicInteger ourNextSchedulerId = new AtomicInteger();

	private final String myThreadNamePrefix;
	private final AutowiringSpringBeanJobFactory mySpringBeanJobFactory;
	private final SchedulerFactoryBean myFactory = new SchedulerFactoryBean();
	private final Properties myProperties = new Properties();

	private Scheduler myScheduler;
	private String myInstanceName;

	public BaseHapiScheduler(String theThreadNamePrefix, AutowiringSpringBeanJobFactory theSpringBeanJobFactory) {
		myThreadNamePrefix = theThreadNamePrefix;
		mySpringBeanJobFactory = theSpringBeanJobFactory;
	}


	void setInstanceName(String theInstanceName) {
		myInstanceName = theInstanceName;
	}


	int nextSchedulerId() {
		return ourNextSchedulerId.getAndIncrement();
	}

	@Override
	public void init() throws SchedulerException {
		setProperties();
		if (myInstanceName != null && myInstanceName.equals("clustered")) {
			myFactory.setConfigLocation(new ClassPathResource("quartz.properties"));
			setDatasourcePropertiesFromEnv();
		}
		myFactory.setQuartzProperties(myProperties);
		myFactory.setBeanName(myInstanceName);
		myFactory.setSchedulerName(myThreadNamePrefix);
		myFactory.setJobFactory(mySpringBeanJobFactory);
		massageJobFactory(myFactory);
		try {
			Validate.notBlank(myInstanceName, "No instance name supplied");
			myFactory.afterPropertiesSet();
		} catch (Exception e) {
			throw new SchedulerException(Msg.code(1633) + e);
		}

		myScheduler = myFactory.getScheduler();
		myScheduler.standby();
	}

	private void setDatasourcePropertiesFromEnv() {
		String datasourceUrl = System.getenv("org.quartz.dataSource.quartzds.URL");
		if (datasourceUrl != null) {
			addProperty("org.quartz.dataSource.quartzds.URL", datasourceUrl);
		}
//		datasource_driver: com.microsoft.sqlserver.jdbc.SQLServerDriver
//		datasource_initial_pool_size: '100'
//		datasource_max_idle_size: '100'
//		datasource_max_pool_size: '100'
//		datasource_min_idle_size: '100'
//		datasource_user_identity: ef30e41a-256b-4751-b68e-3866abbd02a2
//		datasource_failovergroup_name: fog-titanhim-dev-us-001.database.windows.net
//		use_datasource_user_identity: true
//		database_name: titan-him-part20
//		datasource_url: jdbc:sqlserver://fog-titanhim-dev-us-001.database.windows.net:1433;database=titan-him-part20;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;
//		org.quartz.dataSource.quartzds.URL: jdbc:sqlserver://fog-titanhim-dev-us-001.database.windows.net:1433;database=titan-him-part20;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;
//		datasource_username: DXxt7Shwz8AKCTgFmeQ6

		String useDatasourceUserIdentityString = System.getenv("use_datasource_user_identity");
		boolean isUsingDatasourceUserIdentity = false;

		if (StringUtils.isNotBlank(useDatasourceUserIdentityString)) {
			useDatasourceUserIdentityString = useDatasourceUserIdentityString.toLowerCase(Locale.ROOT);
			isUsingDatasourceUserIdentity = Boolean.parseBoolean(useDatasourceUserIdentityString);
		}

		if (isUsingDatasourceUserIdentity) {
			ourLog.info("Connecting using managed identity - isUsingDatasourceUserIdentity = {}", isUsingDatasourceUserIdentity);

			SQLServerDataSource sqlServerDataSource = createSQLServerDataSource();
			if (sqlServerDataSource != null) {
				ourLog.info("Connecting using managed identity - myFactory.setDataSource(sqlServerDataSource) - isUsingDatasourceUserIdentity = {} setting c3p0 datasource to sqlServerDataSource traceID: {}", isUsingDatasourceUserIdentity, sqlServerDataSource.toString());
				myFactory.setDataSource(sqlServerDataSource);
			} else {
				ourLog.error("Connecting using managed identity - isUsingDatasourceUserIdentity = {} FAILED - check the logs for an explanation of what went wrong.", isUsingDatasourceUserIdentity);
				throw new RuntimeException("Connecting using managed identity - unable to create a sqlServerDataSource - FAILED - check the logs for an explanation of what went wrong.");
			}
		} else {
			String datasourceUserName = System.getenv("datasource_username");
			String datasourcePassword = System.getenv("datasource_password");
			if (datasourceUserName != null && datasourcePassword != null) {
				addProperty("org.quartz.dataSource.quartzds.user", datasourceUserName);
				addProperty("org.quartz.dataSource.quartzds.password", datasourcePassword);
			}
		}
	}

	/**
	 *
	 * @return SQLServerDataSource - a SQLServerDataSource that can connect to an Azure SQL server via Managed Identity
	 */
	private SQLServerDataSource createSQLServerDataSource() {
		// logic for ManagedIdentity connection to c3p0
		final String sqlDbUserIdentity = System.getenv("datasource_user_identity");
		final String dbFogServerName = System.getenv("datasource_failovergroup_name");
		final String databaseName = System.getenv("database_name");
		final String datasourceDriver = System.getenv("datasource_driver");

		ourLog.info("Connecting using managed identity - sqlDbUserIdentity = {}, dbFogServerName: {}, databaseName: {}, datasourceDriver: {}", sqlDbUserIdentity, dbFogServerName, databaseName, datasourceDriver);

		if (StringUtils.isBlank(sqlDbUserIdentity) || StringUtils.isBlank(dbFogServerName) || StringUtils.isBlank(databaseName)) {
			ourLog.error("Connecting using managed identity - Missing Environment Variables - unable to create a SQLServerDataSource -> sqlDbUserIdentity = {}, dbFogServerName: {}, databaseName: {}", sqlDbUserIdentity, dbFogServerName, databaseName);
			return null;
		}
		ourLog.info("Connecting using managed identity - START: Retrieve accessToken sqlDbUserIdentity = {}, dbFogServerName: {}, databaseName: {}, datasourceDriver: {}", sqlDbUserIdentity, dbFogServerName, databaseName, datasourceDriver);
		AccessToken accessToken = getDatabaseAccessToken(sqlDbUserIdentity, dbFogServerName, databaseName);

		if (accessToken == null) {
			ourLog.error("Connecting using managed identity - END: Retrieve accessToken - accessToken is null -> sqlDbUserIdentity = {}, dbFogServerName: {}, databaseName: {}, datasourceDriver: {}", sqlDbUserIdentity, dbFogServerName, databaseName, datasourceDriver);
		}

		if (accessToken != null) {

			final String token = accessToken.getToken();
			final String tokenExpiresAt = accessToken.getExpiresAt().toString();
			ourLog.info("Connecting using managed identity - END: Retrieved accessToken - accessToken is {} tokenExpiresAt: {} -> sqlDbUserIdentity = {}, dbFogServerName: {}, databaseName: {}, datasourceDriver: {}", token, tokenExpiresAt, sqlDbUserIdentity, dbFogServerName, databaseName, datasourceDriver);
			addProperty("org.quartz.dataSource.quartzds.driver", datasourceDriver);
			SQLServerDataSource sqlServerDataSource = new SQLServerDataSource();
			sqlServerDataSource.setServerName(dbFogServerName);
			sqlServerDataSource.setDatabaseName(databaseName);
			sqlServerDataSource.setAccessToken(token);
			// validate that we can make a connection to the dataSource
			connectToDatasource(sqlServerDataSource);
			ourLog.info("Connecting using managed identity - Setting the SQLServerDataSource via the myFactory.setDataSource(sqlServerDataSource) for clustered Quartz jobs - sqlServerDataSource traceID: {}", sqlServerDataSource.toString());
			return sqlServerDataSource;
		}
		return null;
	}

	private AccessToken getDatabaseAccessToken(final String sqlDbUserIdentity, final String dbFogServerName,
													  final String databaseName) {
		ourLog.info("Connecting using managed identity sqlDbUserIdentity: {}, dbFogServerName: {}, databaseName: {}",
			sqlDbUserIdentity, dbFogServerName, databaseName);
		ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder()
			.clientId(sqlDbUserIdentity)
			.retryTimeout(duration -> Duration.ofSeconds(5L))
			.maxRetry(5)
			.enableAccountIdentifierLogging()
			.build();

		// Get the accessToken
		TokenRequestContext request = new TokenRequestContext();
		request.addScopes("https://database.windows.net//.default");

		String token = null;
		String tokenExpiresAt = null;

		ourLog.info("Connecting using managed identity sqlDbUserIdentity: {}, dbFogServerName: {}, databaseName: {}. attempt to retrieve accessToken.", sqlDbUserIdentity, dbFogServerName, databaseName);
		AccessToken accessToken = null;
		try {
			accessToken = managedIdentityCredential.getToken(request).block();
		} catch (RuntimeException re) {
			ourLog.error("Connecting using managed identity sqlDbUserIdentity: {}, dbFogServerName: {}, databaseName: {}. Unable to retrieve accessToken. Exception Message: {}", sqlDbUserIdentity, dbFogServerName, databaseName, re.getMessage());
			ourLog.error("Connecting using managed identity failed", re);
		}
		ourLog.info("Connecting using managed identity sqlDbUserIdentity: {}, dbFogServerName: {}, databaseName: {}. attempted to retrieve accessToken - received accessToken: {}", sqlDbUserIdentity, dbFogServerName, databaseName, accessToken);
		if (accessToken == null) {
			ourLog.error("Connecting using managed identity sqlDbUserIdentity: {}, dbFogServerName: {}, databaseName: {}. Unable to retrieve accessToken.", sqlDbUserIdentity, dbFogServerName, databaseName);
			return null;
		}

		token = accessToken.getToken();
		tokenExpiresAt = accessToken.getExpiresAt().toString();

		ourLog.info("Connecting using managed identity sqlDbUserIdentity: {}, dbFogServerName: {}, databaseName: {}. Retrieved an accessToken for the SQLServerDataSource. token: {} tokenExpiresAt: {}", sqlDbUserIdentity, dbFogServerName, databaseName, token, tokenExpiresAt);
		return accessToken;
	}

	private void connectToDatasource(DataSource ds) {
		Connection connection = null;
		try {
			connection = ds.getConnection();
			Statement stmt = connection.createStatement();
			//noinspection SqlResolve
			ResultSet rs = stmt.executeQuery("SELECT SUSER_SNAME()");
			if (rs.next()) {
				final String dataSourceUsername = rs.getString(1);
				ourLog.info("connectToDatasource - Successfully connected to the database with username: {}", dataSourceUsername);
			}
		} catch (Exception e) {
			ourLog.error("connectToDatasource - Connecting using DataSource threw an exception", e);
			ourLog.error("connectToDatasource - Error connecting to the database via the passed in DataSource. Exception message: {}", e.getMessage());
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (Exception ex) {
					ourLog.error("connectToDatasource - closing connection failure", ex);
				}
			}
		}
	}


	protected void massageJobFactory(SchedulerFactoryBean theFactory) {
		// nothing by default
	}

	protected void setProperties() {
		addProperty("org.quartz.threadPool.threadCount", "4");
		myProperties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, myInstanceName + "-" + nextSchedulerId());
		addProperty("org.quartz.threadPool.threadNamePrefix", getThreadPrefix());
	}

	@Nonnull
	private String getThreadPrefix() {
		return myThreadNamePrefix + "-" + myInstanceName;
	}

	protected void addProperty(String key, String value) {
		myProperties.put(key, value);
	}

	@Override
	public void start() {
		if (myScheduler == null) {
			throw new ConfigurationException(Msg.code(1634) + "Attempt to start uninitialized scheduler");
		}
		try {
			ourLog.info("Starting scheduler {}", getThreadPrefix());
			myScheduler.start();
		} catch (SchedulerException e) {
			ourLog.error("Failed to start up scheduler", e);
			throw new ConfigurationException(Msg.code(1635) + "Failed to start up scheduler", e);
		}
	}

	@Override
	public void shutdown() {
		if (myScheduler == null) {
			return;
		}
		try {
			myScheduler.shutdown(true);
		} catch (SchedulerException e) {
			ourLog.error("Failed to shut down scheduler", e);
			throw new ConfigurationException(Msg.code(1636) + "Failed to shut down scheduler", e);
		}
	}

	@Override
	public boolean isStarted() {
		try {
			return myScheduler != null && myScheduler.isStarted();
		} catch (SchedulerException e) {
			ourLog.error("Failed to determine scheduler status", e);
			return false;
		}
	}

	@Override
	public void clear() throws SchedulerException {
		myScheduler.clear();
	}

	@Override
	public void logStatusForUnitTest() {
		try {
			Set<JobKey> keys = myScheduler.getJobKeys(GroupMatcher.anyGroup());
			String keysString = keys.stream().map(t -> t.getName()).collect(Collectors.joining(", "));
			ourLog.info("Local scheduler has jobs: {}", keysString);
		} catch (SchedulerException e) {
			ourLog.error("Failed to get log status for scheduler", e);
			throw new InternalErrorException(Msg.code(1637) + "Failed to get log status for scheduler", e);
		}
	}

	@Override
	public void scheduleJob(long theIntervalMillis, ScheduledJobDefinition theJobDefinition) {
		Validate.isTrue(theIntervalMillis >= 100);

		Validate.notNull(theJobDefinition);
		Validate.notNull(theJobDefinition.getJobClass());
		Validate.notBlank(theJobDefinition.getId());

		JobKey jobKey = new JobKey(theJobDefinition.getId(), theJobDefinition.getGroup());
		TriggerKey triggerKey = new TriggerKey(theJobDefinition.getId(), theJobDefinition.getGroup());

		JobDetailImpl jobDetail = new NonConcurrentJobDetailImpl();
		jobDetail.setJobClass(theJobDefinition.getJobClass());
		jobDetail.setKey(jobKey);
		jobDetail.setJobDataMap(new JobDataMap(theJobDefinition.getJobData()));

		ScheduleBuilder<? extends Trigger> schedule = SimpleScheduleBuilder
			.simpleSchedule()
			.withIntervalInMilliseconds(theIntervalMillis)
			.repeatForever();

		Trigger trigger = TriggerBuilder.newTrigger()
			.forJob(jobDetail)
			.withIdentity(triggerKey)
			.startNow()
			.withSchedule(schedule)
			.build();

		Set<? extends Trigger> triggers = Sets.newHashSet(trigger);
		try {
			myScheduler.scheduleJob(jobDetail, triggers, true);
		} catch (SchedulerException e) {
			ourLog.error("Failed to schedule job", e);
			throw new InternalErrorException(Msg.code(1638) + e);
		}

	}

	@VisibleForTesting
	@Override
	public Set<JobKey> getJobKeysForUnitTest() throws SchedulerException {
		return myScheduler.getJobKeys(GroupMatcher.anyGroup());
	}

	private static class NonConcurrentJobDetailImpl extends JobDetailImpl {
		private static final long serialVersionUID = 5716197221121989740L;

		// All HAPI FHIR jobs shouldn't allow concurrent execution
		@Override
		public boolean isConcurrentExectionDisallowed() {
			return true;
		}
	}
}
