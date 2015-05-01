package com.appdynamics.extensions.logmonitor;

import static com.appdynamics.extensions.logmonitor.Constants.*;
import static com.appdynamics.extensions.logmonitor.util.LogMonitorUtil.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.log4j.Logger;
import org.bitbucket.kienerj.OptimizedRandomAccessFile;

import com.appdynamics.extensions.logmonitor.config.Log;
import com.appdynamics.extensions.logmonitor.exceptions.FileException;
import com.appdynamics.extensions.logmonitor.processors.FilePointer;
import com.appdynamics.extensions.logmonitor.processors.FilePointerProcessor;

/**
 * @author Florencio Sarmiento
 *
 */
public class LogMonitorTask implements Callable<LogMetrics> {
	
	private static final Logger LOGGER = 
			Logger.getLogger("com.singularity.extensions.logmonitor.LogMonitorTask");
	
	private FilePointerProcessor filePointerProcessor;
	
	private Log log;
	
	public LogMonitorTask(FilePointerProcessor filePointerProcessor, Log log) {
		this.filePointerProcessor = filePointerProcessor;
		this.log = log;
	}

	public LogMetrics call() throws Exception {
		String dirPath = resolveDirPath(log.getLogDirectory());
		LOGGER.info("Log monitor task started...");
		
		LogMetrics logMetrics = new LogMetrics();
		OptimizedRandomAccessFile randomAccessFile = null;
		
		long curFilePointer = 0;
		
		try {
			File file = getLogFile(dirPath);
			randomAccessFile = new OptimizedRandomAccessFile(file, "r");
			long fileSize = randomAccessFile.length();
			String dynamicLogPath = dirPath + log.getLogName();
			curFilePointer = getCurrentFilePointer(dynamicLogPath, file.getPath(), fileSize);
			Pattern searchPattern = createPattern(log.getSearchStrings(), log.getMatchExactString());
			
			LOGGER.info(String.format("Processing log file [%s], starting from [%s]", 
					file.getPath(), curFilePointer));
			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(String.format("Searching for [%s]", searchPattern.pattern()));
			}
			
			randomAccessFile.seek(curFilePointer);
			
			String currentLine = null;
			
			while((currentLine = randomAccessFile.readLine()) != null) {
				incrementWordCountIfSearchStringMatched(searchPattern, currentLine, logMetrics);
				curFilePointer = randomAccessFile.getFilePointer();
			}
			
			if (LOGGER.isDebugEnabled() && logMetrics.getMetrics().isEmpty()) {
				LOGGER.debug("No word metrics to upload, no matches found!");
			}
			
			logMetrics.add(getLogNamePrefix() + FILESIZE_METRIC_NAME, BigInteger.valueOf(fileSize));
			
			setNewFilePointer(dynamicLogPath, file.getPath(), curFilePointer);
			
			LOGGER.info(String.format("Sucessfully processed log file [%s]", 
					file.getPath()));
			
		} finally {
			closeRandomAccessFile(randomAccessFile);
		}
		
		return logMetrics;
	}
	
	private File getLogFile(String dirPath) throws FileNotFoundException {
		File directory = new File(dirPath);
		File logFile = null;
		
		if (directory.isDirectory()) {
			FileFilter fileFilter = new WildcardFileFilter(log.getLogName());
			File[] files = directory.listFiles(fileFilter);
			
			if (files != null && files.length > 0) {
				logFile = getLatestFile(files);
				
				if (!logFile.canRead()) {
					throw new FileException(
							String.format("Unable to read file [%s]", logFile.getPath()));
				}
				
			} else {
				throw new FileNotFoundException(
						String.format("Unable to find any file with name [%s] in [%s]", 
								log.getLogName(), dirPath));
			}
			
		} else {
			throw new FileNotFoundException(
					String.format("Directory [%s] not found. Ensure it is a directory.", 
							dirPath));
		}
		
		return logFile;
	}
	
	private String resolveDirPath(String confDirPath) {
		String resolvedPath = resolvePath(confDirPath);
		
		if (!resolvedPath.endsWith(File.separator)) {
			resolvedPath = resolvedPath + File.separator;
		}
		
		return resolvedPath;
	}
	
	private File getLatestFile(File[] files) {
		File latestFile = null;
		long lastModified = Long.MIN_VALUE;
		
		for (File file : files) {
			if (file.lastModified() > lastModified) {
				latestFile = file;
				lastModified = file.lastModified();
			}
		}
		
		return latestFile;
	}
    
    private long getCurrentFilePointer(String dynamicLogPath, 
    		String actualLogPath, long fileSize) {
    	
    	FilePointer filePointer = 
    			filePointerProcessor.getFilePointer(dynamicLogPath, actualLogPath);
    	
    	long currentPosition = filePointer.getLastReadPosition().get();
    	
    	if (isFilenameChanged(filePointer.getFilename(), actualLogPath) || 
    		isLogRotated(fileSize, currentPosition)) {
    		
    		if (LOGGER.isDebugEnabled()) {
    			LOGGER.debug("Filename has either changed or rotated, resetting position to 0");
    		}

    		currentPosition = 0;
    	} 
    	
    	return currentPosition;
    }
	
	private boolean isLogRotated(long fileSize, long startPosition) {
		return fileSize < startPosition;
	}
	
	private boolean isFilenameChanged(String oldFilename, String newFilename) {
		return !oldFilename.equals(newFilename);
	}
	
	private void incrementWordCountIfSearchStringMatched(Pattern pattern, 
			String stringToCheck, LogMetrics logMetrics) {
		Matcher matcher = pattern.matcher(stringToCheck);
		String logMetricPrefix = getSearchStringPrefix();
		
		while (matcher.find()) {
			String word = WordUtils.capitalizeFully(matcher.group()).trim();
			logMetrics.add(logMetricPrefix + word);
		}
	}
	
	private void setNewFilePointer(String dynamicLogPath, 
    		String actualLogPath, long lastReadPosition) {
		filePointerProcessor.updateFilePointer(dynamicLogPath, actualLogPath, lastReadPosition);
	}
	
	private String getSearchStringPrefix() {
		return String.format("%s%s%s", getLogNamePrefix(), 
				SEARCH_STRING, METRIC_PATH_SEPARATOR);
	}
	
	private String getLogNamePrefix() {
		String displayName = StringUtils.isBlank(log.getDisplayName()) ? 
				log.getLogName() : log.getDisplayName();
				
		return displayName + METRIC_PATH_SEPARATOR; 
	}
}
