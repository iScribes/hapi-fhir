package ca.uhn.fhir.batch2.model;

import ca.uhn.fhir.jpa.util.JsonDateDeserializer;
import ca.uhn.fhir.jpa.util.JsonDateSerializer;
import ca.uhn.fhir.model.api.IModelJson;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.Validate;

import java.util.Date;
import java.util.Map;

public class WorkChunk implements IModelJson {

	@JsonProperty("id")
	private String myId;

	@JsonProperty("sequence")
	private int mySequence;

	@JsonProperty("status")
	private StatusEnum myStatus;

	@JsonProperty("jobDefinitionId")
	private String myJobDefinitionId;

	@JsonProperty("jobDefinitionVersion")
	private int myJobDefinitionVersion;

	@JsonProperty("targetStepId")
	private String myTargetStepId;

	@JsonProperty("instanceId")
	private String myInstanceId;

	@JsonProperty("data")
	private Map<String, Object> myData;

	@JsonProperty("createTime")
	@JsonSerialize(using = JsonDateSerializer.class)
	@JsonDeserialize(using = JsonDateDeserializer.class)
	private Date myCreateTime;

	@JsonProperty("startTime")
	@JsonSerialize(using = JsonDateSerializer.class)
	@JsonDeserialize(using = JsonDateDeserializer.class)
	private Date myStartTime;

	@JsonProperty("endTime")
	@JsonSerialize(using = JsonDateSerializer.class)
	@JsonDeserialize(using = JsonDateDeserializer.class)
	private Date myEndTime;

	@JsonProperty(value = "recordsProcessed", access = JsonProperty.Access.READ_ONLY)
	private Integer myRecordsProcessed;

	@JsonProperty(value = "errorMessage", access = JsonProperty.Access.READ_ONLY)
	private String myErrorMessage;

	/**
	 * Constructor
	 */
	public WorkChunk() {
		super();
	}

	public Date getStartTime() {
		return myStartTime;
	}

	public WorkChunk setStartTime(Date theStartTime) {
		myStartTime = theStartTime;
		return this;
	}

	public Date getEndTime() {
		return myEndTime;
	}

	public WorkChunk setEndTime(Date theEndTime) {
		myEndTime = theEndTime;
		return this;
	}

	public Integer getRecordsProcessed() {
		return myRecordsProcessed;
	}

	public WorkChunk setRecordsProcessed(Integer theRecordsProcessed) {
		myRecordsProcessed = theRecordsProcessed;
		return this;
	}

	public StatusEnum getStatus() {
		return myStatus;
	}

	public WorkChunk setStatus(StatusEnum theStatus) {
		myStatus = theStatus;
		return this;
	}

	public String getJobDefinitionId() {
		return myJobDefinitionId;
	}

	public WorkChunk setJobDefinitionId(String theJobDefinitionId) {
		Validate.notBlank(theJobDefinitionId);
		myJobDefinitionId = theJobDefinitionId;
		return this;
	}

	public int getJobDefinitionVersion() {
		return myJobDefinitionVersion;
	}

	public WorkChunk setJobDefinitionVersion(int theJobDefinitionVersion) {
		Validate.isTrue(theJobDefinitionVersion >= 1);
		myJobDefinitionVersion = theJobDefinitionVersion;
		return this;
	}

	public String getTargetStepId() {
		return myTargetStepId;
	}

	public WorkChunk setTargetStepId(String theTargetStepId) {
		Validate.notBlank(theTargetStepId);
		myTargetStepId = theTargetStepId;
		return this;
	}

	public Map<String, Object> getData() {
		return myData;
	}

	public WorkChunk setData(Map<String, Object> theData) {
		myData = theData;
		return this;
	}

	public String getInstanceId() {
		return myInstanceId;
	}

	public WorkChunk setInstanceId(String theInstanceId) {
		myInstanceId = theInstanceId;
		return this;
	}

	public String getId() {
		return myId;
	}

	public WorkChunk setId(String theId) {
		Validate.notBlank(theId);
		myId = theId;
		return this;
	}

	public int getSequence() {
		return mySequence;
	}

	public void setSequence(int theSequence) {
		mySequence = theSequence;
	}

	public Date getCreateTime() {
		return myCreateTime;
	}

	public void setCreateTime(Date theCreateTime) {
		myCreateTime = theCreateTime;
	}

	public void setErrorMessage(String theErrorMessage) {
		myErrorMessage = theErrorMessage;
	}

	public String getErrorMessage() {
		return myErrorMessage;
	}
}
