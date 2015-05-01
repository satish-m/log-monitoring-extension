package com.appdynamics.extensions.logmonitor;

import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.TaskOutput;

public class LogMonitorITest {
	
	private LogMonitor classUnderTest = new LogMonitor();
	
	@Test
	public void testMetricsCollection() throws Exception {
		Map<String, String> args = Maps.newHashMap();
		args.put("config-file","src/test/resources/conf/config.yaml");
		
		TaskOutput result = classUnderTest.execute(args, null);
		assertTrue(result.getStatusMessage().contains("successfully completed"));
	}

}
