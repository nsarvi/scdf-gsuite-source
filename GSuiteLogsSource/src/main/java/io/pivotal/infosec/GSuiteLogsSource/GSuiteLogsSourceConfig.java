package io.pivotal.infosec.GSuiteLogsSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.messaging.MessageChannel;

import com.google.api.client.util.IOUtils;

/**
 * 
 * @author nsarvi
 *
 */
@EnableBinding(Source.class)
@EnableConfigurationProperties(GSuiteLogsSourceOptionsMetadata.class)
public class GSuiteLogsSourceConfig extends AbstractEndpoint{

	private static final Logger logger = LoggerFactory.getLogger(GSuiteLogsSourceConfig.class);
	
	@Autowired
	private GSuiteLogsSourceOptionsMetadata properties;

	
	private  ExecutorService executorService;


	/** Resource to the Service Account's Private Key file */
	@Autowired
	private ResourceLoader resourceLoader;
	
  
	
	private final static String PREFIX="SERVICE_ACCOUNT";
	private final static String SUFFIX="PK12";
	
	private  static File serviceAccountPK12File=null;
	
	
	@Autowired
	Source outputChannel;
	
	@Bean
    public MessageChannel generatorChannel() {
		logger.info("generatorChannel");
        return MessageChannels.direct().get();
    }
	
	
//	@Bean
//    public IntegrationFlow gsuiteLogsflow(MessageChannel generatorChannel) {
//		logger.info("Gsuite Logs Flow");
//        return IntegrationFlows.from(generatorChannel).channel(source.output()).get();
//    }


	@Override
	protected void doStart() {
		Integer fixedDelay=new Integer(properties.getFixedDelay());
		Integer startRange=new Integer(properties.getStartRange());
		this.executorService=Executors.newSingleThreadExecutor();
		executorService.execute(new GSuiteLogsProducer(outputChannel,serviceAccountPK12File,properties.getStateDate(),startRange,fixedDelay,properties.getUserMail(),properties.getServiceAccountEmail()));
	}


	@Override
	protected void doStop() {
		
	}
	
	@PostConstruct
	public void initServiceAccountFile()  {
		Resource serviceAccountPK12Resource = resourceLoader.getResource("classpath:IT-StarSec-Cognition-d40aa2be72f9.p12");
		try {
			serviceAccountPK12File=stream2file(serviceAccountPK12Resource.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}


	public  File stream2file (InputStream in) throws IOException {
        final File tempFile = File.createTempFile(PREFIX, SUFFIX);
        FileOutputStream out = new FileOutputStream(tempFile);
        IOUtils.copy(in, out);
        return tempFile;
    }
	
		
}
	