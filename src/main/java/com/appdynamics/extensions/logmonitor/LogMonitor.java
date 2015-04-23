package com.appdynamics.extensions.logmonitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.log4j.Logger;
import org.bitbucket.kienerj.OptimizedRandomAccessFile;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

/**
 * Monitors the log file and counts the no of occurrences of the search terms provided
 * 
 * @author Florencio Sarmiento
 *
 */
public class LogMonitor extends AManagedMonitor {
	
	public static final Logger LOGGER = Logger.getLogger("com.singularity.extensions.LogMonitor");
	
	public static final String ARG_FILEPATH_PREFIX = "file.path.";
	
	public static final String ARG_FILENAME_ALIAS_PREFIX = "filename.alias.for.file.";
	
	public static final String ARG_SEARCH_STRING_PREFIX = "search.strings.for.file.";
	
	public static final String ARG_MATCH_EXACT_STRING = "match.exact.string.in.file.";
	
	public static final String DEFAULT_DELIMETER = "|";
	
	public static final String DEFAULT_METRIC_PREFIX = "Custom Metrics|LogMonitor|";
	
	public static final String SEARCH_STRING_METRIC_PREFIX = "Search String|";
	
	public static final String FILESIZE_METRIC_NAME = "File size (Bytes)";
	
	public static final String FILEPOINTER_FILENAME = "filepointer.txt";
	
	public static final String SEARCH_STRING_RAW_DELIMETER = ",";
	
	private String metricPrefix;
	
	private Map<String, Long> filePointers = new ConcurrentHashMap<String, Long>();
	
	public LogMonitor() {
		LOGGER.info(String.format("Using Monitor Version [%s]", getImplementationVersion()));
		initialiseFilePointers();
	}
	
	public TaskOutput execute(Map<String, String> args,
			TaskExecutionContext arg1) throws TaskExecutionException {
		LOGGER.info("Starting the Logger Monitoring task");
		
		debugLog("Args received were: " + args);
		
		if (args == null || args.isEmpty()) {
			throw new TaskExecutionException("You must provide at least one required filepath and search string!");
		}
		
		setMetricPrefix(args);
		boolean filePointerFileRequiresUpdate = false;
		
		for (String key : args.keySet()) {

			if (key.startsWith(ARG_FILEPATH_PREFIX)) {
				String filepath = args.get(key);

				if (StringUtils.isBlank(filepath)) {
					LOGGER.error("Filepath not provided for " + key);
					continue;
				}

				String index = key.substring(ARG_FILEPATH_PREFIX.length());
				String rawSearchString = args.get(ARG_SEARCH_STRING_PREFIX + index);

				if (StringUtils.isBlank(rawSearchString)) {
					LOGGER.error(String.format("No search strings provided for %s%s", 
							ARG_SEARCH_STRING_PREFIX, index));
					continue;
				}
				
				String filenameAlias = args.get(ARG_FILENAME_ALIAS_PREFIX + index);
				boolean matchExactWord = Boolean.valueOf(args.get(ARG_MATCH_EXACT_STRING + index));

				Map<String, BigInteger> wordMetrics = new HashMap<String, BigInteger>();
				String formattedSearchString = reformatSearchStringAndInitialiseWordMetrics(rawSearchString, wordMetrics, matchExactWord);
				processLogFile(filepath, filenameAlias, formattedSearchString, wordMetrics);
				filePointerFileRequiresUpdate = true;
			}
		}
		
		if (filePointerFileRequiresUpdate) {
			updateFilePointerFile();
		}
		
		return new TaskOutput("LogMonitor Task Completed");
	}
	
    private void initialiseFilePointers() {
    	debugLog("Initialising filepointers");
    	File file = new File(getFilePointerPath());
    	
    	OptimizedRandomAccessFile randomAccessFile = null;
		
		try {
			randomAccessFile = new OptimizedRandomAccessFile(file, "rws");
			
			String currentLine = null;
			
			while((currentLine = randomAccessFile.readLine()) != null) {
				List<String> stringList = Lists.newArrayList(Splitter
						.on(DEFAULT_DELIMETER)
						.trimResults()
						.omitEmptyStrings()
						.split(currentLine));
				
				String filepath = null;
				String stringFilePointer = null;
				int index = 0;
				
				for (String value : stringList) {
					if (index == 0) {
						filepath = value;
					} else {
						stringFilePointer = value;
						break;
					}
					
					index++;
				}
				
				if (StringUtils.isNotBlank(filepath)) {
					Long filePointer = convertFilePointerToLong(stringFilePointer);
					filePointers.put(filepath, filePointer);
				}
			}
			
		} catch (Exception e) {
			LOGGER.error(String.format(
					"Unfortunately an error occurred while reading the file %s", file.getPath()),
					e);
			return;
			
		} finally {
			closeRandomAccessFile(file.getPath(), randomAccessFile);
		}
		
		debugLog("Filepointers initialised with: " + filePointers);
    }
	
	private void setMetricPrefix(Map<String, String> args) {
		metricPrefix = args.get("metricPrefix");
		
		if (StringUtils.isBlank(metricPrefix)) {
			metricPrefix = DEFAULT_METRIC_PREFIX;
			
		} else {
			metricPrefix = metricPrefix.trim();
			
			if (!metricPrefix.endsWith(DEFAULT_DELIMETER)) {
				metricPrefix = metricPrefix + DEFAULT_DELIMETER;
			}
		}
	}
	
	private String reformatSearchStringAndInitialiseWordMetrics(String rawSearchString, Map<String, BigInteger> wordMetrics,
			boolean matchExactWord) {
		List<String> searchStringList = Lists.newArrayList(Splitter.on(SEARCH_STRING_RAW_DELIMETER)
				.trimResults()
				.omitEmptyStrings()
				.split(rawSearchString));

		StringBuilder formattedSearchString = new StringBuilder();
		
		int index = 0;
		
		for (String searchString : searchStringList) {
			searchString = WordUtils.capitalizeFully(searchString);
			wordMetrics.put(searchString, BigInteger.ZERO);
			
			if (matchExactWord) {
				searchString = "(?<=\\s|^)" + Pattern.quote(searchString) + "(?=\\s|$)";
				
			} else {
				searchString =  Pattern.quote(searchString);
			}
			
			if (index > 0) {
				formattedSearchString.append(DEFAULT_DELIMETER);
			}
			
			formattedSearchString.append(searchString);
			index++;
		}
		
		return formattedSearchString.toString();
	}
	
	private void processLogFile(String filepath, String filenameAlias, 
			String searchString, Map<String, BigInteger> wordMetrics) {
		
		debugLog(String.format("Processing file [%s] with search strings [%s]",
				filepath, searchString));
		
		File file = new File(filepath);
		
		if (!file.exists() || !file.canRead()) {
			LOGGER.error(String.format(
					"Unable to read %s. Check that it exists and has the appropriate read permission.", filepath));
			return;
		}
		
		OptimizedRandomAccessFile randomAccessFile = null;
		long fileSize = 0;
		long filePointer = 0;
		
		try {
			randomAccessFile = new OptimizedRandomAccessFile(file, "r");
			
			fileSize = randomAccessFile.length();
			filePointer = getFilePointer(filepath, fileSize);
			
			randomAccessFile.seek(filePointer);
			
			Pattern pattern = Pattern.compile(searchString, Pattern.CASE_INSENSITIVE);
			String currentLineToSearch = null;
			
			while((currentLineToSearch = randomAccessFile.readLine()) != null) {
				incrementWordCountIfSearchStringMatched(pattern, currentLineToSearch, wordMetrics);
				filePointer = randomAccessFile.getFilePointer();
			}
			
		} catch (Exception e) {
			LOGGER.error(String.format(
					"Unfortunately an error occurred while reading the file %s", filepath),
					e);
			return;
			
		} finally {
			closeRandomAccessFile(filepath, randomAccessFile);
		}
		
		String filename = StringUtils.isBlank(filenameAlias) ? file.getName() : filenameAlias;
		
		uploadMetrics(filename, fileSize, wordMetrics);
		filePointers.put(filepath, filePointer);
	}
	
	private long getFilePointer(String filepath, long fileSize) {
		Long tmpFilePointer = filePointers.get(filepath);
		long filePointer = tmpFilePointer != null ? tmpFilePointer : 0;
		
		if (isLogRotated(fileSize, filePointer)) {
			filePointer = 0;
		}
		
		return filePointer;
	}
	
	private boolean isLogRotated(long fileSize, long startPosition) {
		return fileSize < startPosition;
	}
	
	private void incrementWordCountIfSearchStringMatched(Pattern pattern, 
			String stringToCheck, Map<String, BigInteger> wordMetrics) {
		Matcher matcher = pattern.matcher(stringToCheck);
		
		while (matcher.find()) {
			String word = WordUtils.capitalizeFully(matcher.group());
			
			BigInteger count = null;
			
			if (wordMetrics.containsKey(word)) {
				count = BigInteger.ONE.add(wordMetrics.get(word));
				
			} else {
				count = BigInteger.ONE;
			}
			
			wordMetrics.put(word, count);
		}
	}
	
	private void uploadMetrics(String filename, long fileSize, Map<String, BigInteger> wordMetrics) {
		String baseMetricNamePrefix =  String.format("%s%s%s", metricPrefix, filename, DEFAULT_DELIMETER);
		String wordMetricPrefix = baseMetricNamePrefix + SEARCH_STRING_METRIC_PREFIX;
		
		for (Map.Entry<String, BigInteger> metric : wordMetrics.entrySet()) {
			printCollectiveObservedCurrent(wordMetricPrefix + metric.getKey(), metric.getValue());
		}
		
		printCollectiveObservedCurrent(baseMetricNamePrefix + FILESIZE_METRIC_NAME, BigInteger.valueOf(fileSize));
	}
	
    private void printCollectiveObservedCurrent(String metricName, BigInteger metricValue) {
        printMetric(metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );
    }
	
    private void printMetric(String metricName, BigInteger metricValue, String aggregation, String timeRollup, String cluster) {
		MetricWriter metricWriter = getMetricWriter(metricName, aggregation,
				timeRollup, cluster);
        
        String value = metricValue != null ? metricValue.toString() : BigInteger.ZERO.toString();
        
        debugLog(String.format("Sending [%s/%s/%s] metric = %s = %s",
            		aggregation, timeRollup, cluster,
                    metricName, value));
        
        metricWriter.printMetric(value);
    }
    
    private void updateFilePointerFile() {
    	String filePointerPath = getFilePointerPath();
    	debugLog("Updating " + filePointerPath);
    	
    	File file = new File(filePointerPath);
    	FileWriter fileWriter = null;
    	
    	try {
    		fileWriter = new FileWriter(file, false);
    		StringBuilder output = new StringBuilder();
    		
    		for (Map.Entry<String, Long> filePointer : filePointers.entrySet()) {
    			output.append(filePointer.getKey())
    			.append(DEFAULT_DELIMETER)
    			.append(filePointer.getValue())
    			.append(System.getProperty("line.separator"));
    		}
    		
    		fileWriter.write(output.toString());
    		
    	} catch (IOException ex) {
    		LOGGER.error(String.format(
					"Unfortunately an error occurred while reading the file %s", file.getPath()),
					ex);
    		
    	} finally {
    		closeFileWriter(filePointerPath, fileWriter);
    	}
    }
    
    private String getFilePointerPath() {
    	String path = null;
    	
    	try {
    		URL classUrl = LogMonitor.class.getResource(LogMonitor.class.getSimpleName() + ".class");
    		String jarPath = classUrl.toURI().toString();
    		
    		// workaround for jar file
    		jarPath = jarPath.replace("jar:", "").replace("file:", "");
    		
    		if (jarPath.contains("!")) {
    			jarPath = jarPath.substring(0, jarPath.indexOf("!"));
    		}
    		
    		File file = new File(jarPath);
    		String jarDir = file.getParentFile().toURI().getPath();
    		
    		if (jarDir.endsWith(File.separator)) {
    			path = jarDir + FILEPOINTER_FILENAME;
    					
    		} else {
    			path = String.format("%s%s%s", jarDir , 
            			File.separator, FILEPOINTER_FILENAME);
    		}
    		
    	} catch (Exception ex) {
    		LOGGER.warn("Unable to resolve installation dir, finding an alternative.");
    	}
    	
    	if (StringUtils.isBlank(path)) {
    		path = String.format("%s%s%s", new File(".").getAbsolutePath(), 
        			File.separator, FILEPOINTER_FILENAME);
    	}
    	
    	try {
			path = URLDecoder.decode(path, "UTF-8");
			
		} catch (UnsupportedEncodingException e) {
			LOGGER.warn(String.format("Unable to decode file path [%s] using UTF-8", path));
		}
    	
    	LOGGER.info("Using filepointer path: " + path);
    	
    	return path;
    }
    
    private Long convertFilePointerToLong(String stringFilePointer) {
    	Long filePointer = null;
    	
    	try {
    		filePointer = Long.valueOf(stringFilePointer);
    		
    	} catch (NumberFormatException ex) {
    		if (LOGGER.isDebugEnabled()) {
    			LOGGER.debug(String.format("Unable to convert [%s] to long, defaulting to 0", 
    					stringFilePointer));
    		}
    	}
    	
    	return filePointer != null ? filePointer : 0;
    }
	
	private void closeRandomAccessFile(String filepath, OptimizedRandomAccessFile randomAccessFile) {
		if (randomAccessFile != null) {
			try {
				randomAccessFile.close();
			} catch (IOException e) {
				LOGGER.error("Unable to close file " + filepath);
			}
		}
	}
	
	private void closeFileWriter(String filepath, FileWriter fileWriter) {
		if (fileWriter != null) {
			try {
				fileWriter.close();
			} catch (IOException e) {
				LOGGER.error("Unable to close file " + filepath);
			}
		}
	}
	
	private void debugLog(String msg) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug(msg);
		}
	}
	
    public static String getImplementationVersion(){
        return LogMonitor.class.getPackage().getImplementationTitle();
    }

}
