package io.pivotal.infosec.GSuiteLogsSource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.support.GenericMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.admin.reports.Reports;
import com.google.api.services.admin.reports.Reports.Activities;
import com.google.api.services.admin.reports.Reports.Activities.List;
import com.google.api.services.admin.reports.model.Activity;
import com.google.api.services.admin.reports.model.Activity.Events;
import com.google.api.services.admin.reports.model.Activity.Events.Parameters;
import com.google.api.services.admin.reports.ReportsScopes;
/**
 * 
 * @author nsarvi
 *
 */
public class GSuiteLogsProducer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(GSuiteLogsProducer.class);
	
	private static final String primaryEvent="primary_event";
	private static final String originatingAppId="originating_app_id";
	private static final String docId="doc_id";
	private static final String docTitle="doc_title";
	private static final String docType="doc_type";
	private static final String ownerIsTeamDrive="owner_is_team_drive";
	private static final String owner="owner";
	private static final String visibility="visibility";
	private static final String oldValue="old_value";
	private static final String newValue="new_value";
	private static final String sourceFolderTitle="source_folder_title";
	private static final String sourceFolderId="source_folder_id";
	private static final String destinationFolderTitle="destination_folder_title";
	private static final String destinationFolderId="destination_folder_id";
	private static final String oldVisibility="old_visibility";
	private static final String visibilityChange="visibility_change";
	private static final String targetDomain="target_domain";
	private static final String targetUser="target_user";
	private static final String target="target";
	private static final String membershipChangeType="membership_change_type";
	private static final String removedRole="removed_role";
	private static final String addedRole="added_role";
	
	
	private Source outputChannel;
	private File serviceAccountPK12File=null;
	
	private String USER_EMAIL;
	private String SERVICE_ACCOUNT_EMAIL;
	private String startTime;
	private Integer startRange;
	private Integer fixedDelay;
	
	
	/**
	 * @param outputChannel
	 * @param serviceAccountPK12File
	 * @param startTime
	 * @param fixedDelay
	 */
	public GSuiteLogsProducer(Source outputChannel, File serviceAccountPK12File, String startTime, Integer startRange, Integer fixedDelay,String userEmail, String serviceAccountEmail) {
		super();
		this.outputChannel = outputChannel;
		this.serviceAccountPK12File = serviceAccountPK12File;
		this.startTime = startTime;
		this.startRange=startRange;
		this.fixedDelay = fixedDelay;
		this.USER_EMAIL=userEmail;
		this.SERVICE_ACCOUNT_EMAIL=serviceAccountEmail;
	}

	public void run() {
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		String startDateAndTime="";
		try {
			Date sDate = (Date)formatter.parse(startTime);
			startDateAndTime=getISO8601StringForDate(sDate);
			Date endDate=new Date();
			endDate.setTime(sDate.getTime() + startRange * 1000);		
			logger.info("Starting getting earlier Gsuite drive logs");
			while (endDate.before(new Date())) {
				endDate.setTime(sDate.getTime() + startRange * 1000);	
				String endDateAndTime=getISO8601StringForDate(endDate);
				gsuiteLogsSource(startDateAndTime, endDateAndTime);
				endDate.setTime(endDate.getTime() + 1);
				startDateAndTime=getISO8601StringForDate(endDate);
				sDate=endDate;
			}
			logger.info("Completed getting earlier Gsuite logs and waiting for the elapsed time :"+this.fixedDelay);
			try {
				Thread.sleep(this.fixedDelay);
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
				e.printStackTrace();
			}	
			// startTime - currentTime -fixedDelay
			// endTime - currentTime
			endDate=new Date();
			String endDateAndTime;
			while (true) {
				startDateAndTime=getISO8601StringForDate(sDate);
				endDateAndTime=getISO8601StringForDate(endDate);
				gsuiteLogsSource(startDateAndTime, endDateAndTime);
				try {
					Thread.sleep(this.fixedDelay);
				} catch (InterruptedException e) {
					logger.error(e.getMessage());
					e.printStackTrace();
				}	
				endDate.setTime(endDate.getTime()+1);
				sDate=endDate;
				endDate=new Date();
			}
		} catch (ParseException e1) {
			logger.error(e1.getMessage());
			e1.printStackTrace();
		}

	}

	
	public void gsuiteLogsSource(String startDateAndTime, String endDateAndTime) {
		
		logger.info("Getting GSuite Logs  : start time:"+startDateAndTime+", endTime : "+endDateAndTime);
		Reports reports;
		try {
			reports = getReportsService(USER_EMAIL);
			Activities activities=reports.activities();
			List resultList=activities.list("all", "drive")
					.setStartTime(startDateAndTime)
					.setEndTime(endDateAndTime)
					.setMaxResults(1000);
			com.google.api.services.admin.reports.model.Activities res=resultList.execute();
			java.util.List<com.google.api.services.admin.reports.model.Activity> resList=res.getItems();
			if (resList !=null) {
				Iterator<com.google.api.services.admin.reports.model.Activity> resIterator=resList.iterator();
				com.google.api.services.admin.reports.model.Activity activity=null;
				String logDriveEvents="";
				logger.debug("Total results :"+resList.size());
				while (resIterator.hasNext()) {
					activity=resIterator.next();
					logger.debug(activity.toPrettyString());
					java.util.List<GSuiteDriveLog> gsuiteDrivelogList=convertToPoJo(activity);
					Iterator<GSuiteDriveLog> gSuiteDriveLogsIt=gsuiteDrivelogList.iterator();
					while (gSuiteDriveLogsIt.hasNext()) {
						GSuiteDriveLog gSuiteDriveLog=gSuiteDriveLogsIt.next();
						ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
						try {
							logDriveEvents = ow.writeValueAsString(gSuiteDriveLog);
							outputChannel.output().send(new GenericMessage(logDriveEvents));
						} catch (JsonProcessingException e) {
							logger.error("JSON parsing exception "+e.getMessage());
							e.printStackTrace();
						}
					}
				}
			}
		} catch (GeneralSecurityException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}catch(Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}
		
	
	/**
	 * Build and returns a Reports service object authorized with the service accounts
	 * that act on behalf of the given user.
	 *
	 * @param userEmail The email of the user. Needs permissions to access the Admin APIs.
	 * @return REports service object that is ready to make requests.
	 */
	public  Reports getReportsService(String userEmail) throws GeneralSecurityException,
	    IOException, URISyntaxException {
		
		
		Collection<String> scopes = new HashSet<String>();
		scopes.add(ReportsScopes.ADMIN_REPORTS_AUDIT_READONLY);
		
	  HttpTransport httpTransport = new NetHttpTransport();
	  JacksonFactory jsonFactory = new JacksonFactory();
	  GoogleCredential credential = new GoogleCredential.Builder()
	      .setTransport(httpTransport)
	      .setJsonFactory(jsonFactory)
	      .setServiceAccountId(SERVICE_ACCOUNT_EMAIL)
	      .setServiceAccountScopes(scopes)
	      .setServiceAccountUser(userEmail)
	      .setServiceAccountPrivateKeyFromP12File(serviceAccountPK12File)
	      .build();
	  Reports service = new Reports.Builder(httpTransport, jsonFactory, null)
	      .setHttpRequestInitializer(credential)
	      .setApplicationName("testapp")
	      .build();
	  return service;
	}
	
	private static String getISO8601StringForDate(Date date) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		return dateFormat.format(date);
	}
	
private java.util.List<GSuiteDriveLog> convertToPoJo(Activity activity) {
		
	java.util.List<GSuiteDriveLog> gsuiteDrivelogList=new ArrayList<GSuiteDriveLog>();
	
		if (activity.getEvents() !=null) {
			java.util.List<Events> eventsList=activity.getEvents();
			
			if (eventsList!=null) {
				for (int i=0;i<eventsList.size();i++) {
					
					GSuiteDriveLog gsuiteDrivelog=new GSuiteDriveLog();
					
					gsuiteDrivelog.setEtag(activity.getEtag());
					if (activity.getActor() !=null) {
						gsuiteDrivelog.setEmailId(activity.getActor().getEmail());
						gsuiteDrivelog.setProfileId(activity.getActor().getProfileId());
					}
					if (activity.getId() !=null) {
						gsuiteDrivelog.setCustomerId(activity.getId().getCustomerId());
						gsuiteDrivelog.setDateTime(activity.getId().getTime().toString());
					}
					gsuiteDrivelog.setIpAddress(activity.getIpAddress());
					
					Events events=eventsList.get(i); 		
					gsuiteDrivelog.setEventName(events.getName());
					gsuiteDrivelog.setEventType(events.getType());
					java.util.List<Parameters> paramtersList=events.getParameters();
					if (paramtersList !=null) {
						for (Parameters parameters :paramtersList) {
							if (primaryEvent.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setPrimaryEvent(parameters.getValue());
							} else 
							if (originatingAppId.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setOriginatingAppId(parameters.getValue());
							} else
							if (docId.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setDocId(parameters.getValue());
							} else
							if (docTitle.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setDocTitle(parameters.getValue());
							} else
							if (docType.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setDocType(parameters.getValue());
							} else
							if (ownerIsTeamDrive.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setOwnerIsTeamDrive(parameters.getValue());
							} else
							if (owner.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setOwner(parameters.getValue());
							} else
							if (visibility.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setVisibility(parameters.getValue());
							} else
							if (oldValue.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setOldValue(parameters.getValue());
							} else
							if (newValue.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setNewValue(parameters.getValue());
							} else
							if (sourceFolderTitle.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setSourceFolderTitle(parameters.getValue());
							} else
							if (sourceFolderId.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setSourceFolderId(parameters.getValue());
							} else
							if (destinationFolderTitle.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setDestinationFolderTitle(parameters.getValue());
							} else
							if (destinationFolderId.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setDestinationFolderId(parameters.getValue());
							} else
							if (oldVisibility.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setOldVisibility(parameters.getValue());
							} else
							if (visibilityChange.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setVisibilityChange(parameters.getValue());
							} else
							if (targetDomain.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setTargetDomain(parameters.getValue());
							} else
							if (targetUser.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setTargetUser(parameters.getValue());
							} else
							if (target.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setTarget(parameters.getValue());
							} else
							if (membershipChangeType.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setMembershipChangeType(parameters.getValue());
							} else
							if (removedRole.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setRemovedRole(parameters.getValue());
							} else
							if (addedRole.equalsIgnoreCase(parameters.getName())) {
								gsuiteDrivelog.setAddedRole(parameters.getValue());
							} 
						}
						
					}
					gsuiteDrivelogList.add(gsuiteDrivelog);	
				}
			}	
		}
		return gsuiteDrivelogList;
		
	}

	
}
