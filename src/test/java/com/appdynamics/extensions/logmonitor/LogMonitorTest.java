package com.appdynamics.extensions.logmonitor;

import static com.appdynamics.extensions.logmonitor.LogMonitor.ARG_FILENAME_ALIAS_PREFIX;
import static com.appdynamics.extensions.logmonitor.LogMonitor.ARG_FILEPATH_PREFIX;
import static com.appdynamics.extensions.logmonitor.LogMonitor.ARG_SEARCH_STRING_PREFIX;
import static com.appdynamics.extensions.logmonitor.LogMonitor.DEFAULT_DELIMETER;
import static com.appdynamics.extensions.logmonitor.LogMonitor.FILESIZE_METRIC_NAME;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.appdynamics.extensions.PathResolver;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PathResolver.class, LogMonitor.class})
public class LogMonitorTest {
	
	@Mock
	private MetricWriter mockMetricWriter;
	
	private LogMonitor classUnderTest;
	
	@Before
	public void init() throws Exception {
		mockStatic(PathResolver.class);
		when(PathResolver.resolveDirectory(LogMonitor.class)).thenReturn(getTargetDir());
		whenNew(MetricWriter.class).withArguments(any(AManagedMonitor.class), anyString()).thenReturn(mockMetricWriter);
		classUnderTest = spy(new LogMonitor());
	}
	
	@Test(expected=TaskExecutionException.class)
	public void testWithNullArgsShouldResultInException() throws Exception {
		classUnderTest.execute(null, null);
	}
	
	@Test(expected=TaskExecutionException.class)
	public void testWithEmptyArgsShouldResultInException() throws Exception {
		classUnderTest.execute(new HashMap<String, String>(), null);
	}
	
	@Test
	public void testSingleLogFile() throws Exception {
		String testFilename = "test-log-1.log";
		String testFilepath = this.getClass().getClassLoader().getResource(testFilename).getPath();
		
		Map<String, String> args = new HashMap<String, String>();
		args.put(ARG_FILEPATH_PREFIX + 1, testFilepath);
		args.put(ARG_SEARCH_STRING_PREFIX + 1, "debug, info, error");
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilename, "Debug", 13);
		verifyMetric(testFilename, "Info", 24);
		verifyMetric(testFilename, "Error", 7);
		verifyMetric(testFilename, FILESIZE_METRIC_NAME, getFileSize(testFilepath));
	}
	
	@Test
	public void testSingleLogFileWithAlias() throws Exception {
		String testFilenameAlias = "TestLog";
		String testFilename = "test-log-1.log";
		String testFilepath = this.getClass().getClassLoader().getResource(testFilename).getPath();
		
		Map<String, String> args = new HashMap<String, String>();
		args.put(ARG_FILEPATH_PREFIX + 1, testFilepath);
		args.put(ARG_SEARCH_STRING_PREFIX + 1, "debug, info, error");
		args.put(ARG_FILENAME_ALIAS_PREFIX + 1, testFilenameAlias);
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilenameAlias, "Debug", 13);
		verifyMetric(testFilenameAlias, "Info", 24);
		verifyMetric(testFilenameAlias, "Error", 7);
		verifyMetric(testFilenameAlias, FILESIZE_METRIC_NAME, getFileSize(testFilepath));
	}
	
	@Test
	public void testMultipleLogFiles() throws Exception {
		String testFilename1 = "test-log-1.log";
		String testFilepath1 = this.getClass().getClassLoader().getResource(testFilename1).getPath();
		
		String testFilename2 = "test-log-2.log";
		String testFilepath2 = this.getClass().getClassLoader().getResource(testFilename2).getPath();
		
		Map<String, String> args = new HashMap<String, String>();
		args.put(ARG_FILEPATH_PREFIX + 1, testFilepath1);
		args.put(ARG_SEARCH_STRING_PREFIX + 1, "debug, info, error");
		args.put(ARG_FILEPATH_PREFIX + 2, testFilepath2);
		args.put(ARG_SEARCH_STRING_PREFIX + 2, "trace");
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilename1, "Debug", 13);
		verifyMetric(testFilename1, "Info", 24);
		verifyMetric(testFilename1, "Error", 7);
		verifyMetric(testFilename1, FILESIZE_METRIC_NAME, getFileSize(testFilepath1));
		
		verifyMetric(testFilename2, "Trace", 10);
		verifyMetric(testFilename2, FILESIZE_METRIC_NAME, getFileSize(testFilepath2));
	}
	
	@Test
	public void testLogFileUpdatedWithMoreLogs() throws Exception {
		String originalFilePath = this.getClass().getClassLoader().getResource("test-log-1.log").getPath();
		
		String testFilename = "active-test-log.log";
		String testFilepath = String.format("%s%s%s", getTargetDir().getPath(), File.separator, testFilename);
		
		copyFile(originalFilePath, testFilepath);
		
		Map<String, String> args = new HashMap<String, String>();
		args.put(ARG_FILEPATH_PREFIX + 1, testFilepath);
		args.put(ARG_SEARCH_STRING_PREFIX + 1, "debug, info, error");
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilename, "Debug", 13);
		verifyMetric(testFilename, "Info", 24);
		verifyMetric(testFilename, "Error", 7);
		
		// perform log update
		long fileSizeBeforeUpdate = getFileSize(testFilepath);
		verifyMetric(testFilename, FILESIZE_METRIC_NAME, fileSizeBeforeUpdate);
		
		List<String> logsToAdd = Arrays.asList("",
				new Date() + "|DEBUG|This is the first line", 
				new Date() + "|INFO|This is the second line",
				new Date() + "|INFO|This is the third line",
				new Date() + "|DEBUG|This is the fourth line",
				new Date() + "|DEBUG|This is the fifth line");
		
		updateLogFile(testFilepath, logsToAdd, true);
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilename, "Debug", 3);
		verifyMetric(testFilename, "Info", 2);
		verifyMetric(testFilename, "Error", 0);
		
		long fileSizeAfterUpdate = getFileSize(testFilepath);
		verifyMetric(testFilename, FILESIZE_METRIC_NAME, fileSizeAfterUpdate);
		
		assertTrue("Updated file should've been bigger", fileSizeAfterUpdate > fileSizeBeforeUpdate);
	}
	
	@Test
	public void testLogFileRotated() throws Exception {
		String originalFilePath = this.getClass().getClassLoader().getResource("test-log-2.log").getPath();
		
		String testFilename = "rotated-test-log.log";
		String testFilepath = String.format("%s%s%s", getTargetDir().getPath(), File.separator, testFilename);
		
		copyFile(originalFilePath, testFilepath);
		
		Map<String, String> args = new HashMap<String, String>();
		args.put(ARG_FILEPATH_PREFIX + 1, testFilepath);
		args.put(ARG_SEARCH_STRING_PREFIX + 1, "trace");
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilename, "Trace", 10);
		
		long fileSizeBeforeRotation = getFileSize(testFilepath);
		verifyMetric(testFilename, FILESIZE_METRIC_NAME, fileSizeBeforeRotation);
		
		// rotate log with these string		
		List<String> logsToAdd = Arrays.asList(
				new Date() + "|TRACE|This is the first line", 
				new Date() + "|DEBUG|This is the second line",
				new Date() + "|TRACE|This is the third line",
				new Date() + "|INFO|This is the fourth line",
				new Date() + "|DEBUG|This is the fifth line");
		
		updateLogFile(testFilepath, logsToAdd, false);
		
		classUnderTest.execute(args, null);
		
		verifyMetric(testFilename, "Trace", 2);
		
		long fileSizeAfterRotation = getFileSize(testFilepath);
		verifyMetric(testFilename, FILESIZE_METRIC_NAME, fileSizeAfterRotation);
		
		assertTrue("Rotated log should've been smaller", fileSizeAfterRotation < fileSizeBeforeRotation);		
	}
	
	private void updateLogFile(String filepath, List<String> stringList, boolean append) throws Exception {
    	File file = new File(filepath);
    	FileWriter fileWriter = null;
    	
    	try {
    		fileWriter = new FileWriter(file, append);
    		String output = StringUtils.join(stringList, System.getProperty("line.separator"));
    		fileWriter.write(output);
    		
    	} finally {
    		fileWriter.close();
    	}
	}
	
	private void verifyMetric(String filename, String metricName, long value) throws Exception {
		String metricPrefix = Whitebox.getInternalState(classUnderTest, "metricPrefix");
		String fullMetricName = String.format("%s%s%s%s", metricPrefix, filename, 
				DEFAULT_DELIMETER, metricName);
				
		verifyPrivate(classUnderTest).invoke("printCollectiveObservedCurrent", 
				fullMetricName, BigInteger.valueOf(value));
	}
	
	private long getFileSize(String filepath) throws Exception {
		RandomAccessFile file = new RandomAccessFile(filepath, "r");
		long fileSize = file.length();
		file.close();
		return fileSize;
	}
	
	private void copyFile(String sourceFilePath, String destFilePath) throws Exception {
		FileChannel sourceChannel = null;
	    FileChannel destChannel = null;
	    
	    try {
	        sourceChannel = new FileInputStream(new File(sourceFilePath)).getChannel();
	        destChannel = new FileOutputStream(new File (destFilePath)).getChannel();
	        destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
	        
		} finally {
			sourceChannel.close();
			destChannel.close();
		}
	}
	
	@After
	public void deleteFilePointerFile() {
		String filePointerPath = String.format("%s%s%s", getTargetDir().getPath(),
				File.separator, LogMonitor.FILEPOINTER_FILENAME);
		
		File filePointerFile = new File(filePointerPath);
		
		if (filePointerFile.exists()) {
			filePointerFile.delete();
		}
	}
	
	private File getTargetDir() {
		return new File("./target");
	}

}
