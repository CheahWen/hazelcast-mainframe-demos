package com.hazelcast.certification.process.impl.executorService;

import com.hazelcast.certification.business.ruleengine.RulesResult;
import com.hazelcast.certification.domain.Transaction;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.Config;
import com.hazelcast.config.ExecutorConfig;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.util.concurrent.TimeUnit;

/**
 * This implementation assumes cluster in Client-Server setup. Other
 * implementations may not use Client-Server, create HazelcastInstance
 * accordingly. It uses <b>IExecutorService</b> to execute business
 * rules on the relevant cluster nodes where the data is stored.
 * @author rahul
 *
 */
public class FraudDetectionImpl extends com.hazelcast.certification.process.FraudDetection {

	private final static ILogger log = Logger.getLogger(FraudDetectionImpl.class);
	private static HazelcastInstance HAZELCAST;
	private final static String EXECUTOR_POOL_NAME = "FraudDetectionService";
	
	//Initializing Client with defaults, but add more specific configurations later.
	static {
		HAZELCAST = HazelcastClient.newHazelcastClient();
	}

	@Override
	protected void startFraudDetection() {
		int EXECUTOR_POOL_SIZE = Integer.parseInt(System.getProperty("ExecutorPoolSize"));

		Config config = new Config();
		ExecutorConfig eConfig = config.getExecutorConfig(EXECUTOR_POOL_NAME);
		eConfig.setPoolSize(EXECUTOR_POOL_SIZE).setName(EXECUTOR_POOL_NAME);
		IExecutorService service = HAZELCAST.getExecutorService(EXECUTOR_POOL_NAME);

		ExecutionCallback<RulesResult> callback = new ExecutionCallback<RulesResult>() {
			public void onResponse(RulesResult r) {
				registerResult(r);
			}

			public void onFailure(Throwable throwable) {
				log.severe("Executor task failure: " + throwable.getMessage());
			}
		};
		
		while(!Thread.interrupted()) {
			try {
				Transaction txn = getNextTransaction();
				if(txn != null) {
					FraudDetectionTask task = new FraudDetectionTask();
					task.setTransaction(txn);
					service.submitToKeyOwner(task, getClusterKey(txn), callback);
				}
			} catch (InterruptedException e) {
				log.severe(e);
			} catch (ArrayIndexOutOfBoundsException e) {
				log.info("Bad String received... discarding.");
			}
		}

		try {
			service.shutdown();
			service.awaitTermination(2, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private String getClusterKey(Transaction txn) {
		return txn.getAccountNumber();
	}
	
}