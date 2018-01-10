package io.pivotal.infosec.GSuiteLogsSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ResourceLoader;

/**
 * 
 * @author nsarvi
 *
 */
@ConfigurationProperties
public class GSuiteLogsSourceOptionsMetadata {

	/** Resource to the Service Account's Private Key file */
	@Autowired
	private ResourceLoader resourceLoader;
	
	/** User's email */
	private  String userMail;
	
	/** Email of the Service Account */
	private String serviceAccountEmail;
	
	/** Start date in the format YYYY-MM-DD HH:mm:ss */
	private String stateDate;
	
	/** Start range in seconds */
	private String startRange="120";
	
	
	/** Fixed delay in seconds */
	private String fixedDelay;

	public ResourceLoader getResourceLoader() {
		return resourceLoader;
	}

	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public String getUserMail() {
		return userMail;
	}

	public void setUserMail(String userMail) {
		this.userMail = userMail;
	}

	public String getServiceAccountEmail() {
		return serviceAccountEmail;
	}

	public void setServiceAccountEmail(String serviceAccountEmail) {
		this.serviceAccountEmail = serviceAccountEmail;
	}

	public String getStateDate() {
		return stateDate;
	}

	public void setStateDate(String stateDate) {
		this.stateDate = stateDate;
	}

	public String getFixedDelay() {
		return fixedDelay;
	}

	public void setFixedDelay(String fixedDelay) {
		this.fixedDelay = fixedDelay;
	}

	public String getStartRange() {
		return startRange;
	}

	public void setStartRange(String startRange) {
		this.startRange = startRange;
	}
	
	

	
}
