package com.appdynamics.extensions.logmonitor.util;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bitbucket.kienerj.OptimizedRandomAccessFile;

import com.appdynamics.extensions.PathResolver;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;

/**
 * @author Florencio Sarmiento
 *
 */
public class LogMonitorUtil {
	
    public static String resolvePath(String filename) {
        if(StringUtils.isBlank(filename)){
            return "";
        }
        
        //for absolute paths
        if(new File(filename).exists()){
            return filename;
        }
        
        //for relative paths
        File jarPath = PathResolver.resolveDirectory(AManagedMonitor.class);
        String configFileName = String.format("%s%s%s", jarPath, File.separator, filename);
        return configFileName;
    }
    
	public static Pattern createPattern(Set<String> rawPatterns, boolean matchExactWord) {
		Pattern pattern = null;
		
		if (rawPatterns != null && !rawPatterns.isEmpty()) {
			StringBuilder rawPatternsStringBuilder = new StringBuilder();
			int index = 0;
			
			for (String rawPattern : rawPatterns) {
				if (index > 0) {
					rawPatternsStringBuilder.append("|");
				}
				
				if (matchExactWord) {
					rawPatternsStringBuilder.append("(?<=\\s|^)");
					rawPatternsStringBuilder.append(Pattern.quote(rawPattern.trim()));
					rawPatternsStringBuilder.append("(?=\\s|$)");
					
				} else {
					rawPatternsStringBuilder.append(rawPattern.trim());
				}
				
				index++;
			}
			
			pattern = Pattern.compile(rawPatternsStringBuilder.toString(), 
					Pattern.CASE_INSENSITIVE);
		}
		
		return pattern;
	}
	
	public static void closeRandomAccessFile(OptimizedRandomAccessFile randomAccessFile) {
		if (randomAccessFile != null) {
			try {
				randomAccessFile.close();
			} catch (IOException e) {}
		}
	}
	
    public static BigInteger convertValueToZeroIfNullOrNegative(BigInteger value) {
    	if (value == null || value.compareTo(BigInteger.ZERO) < 0) {
    		return BigInteger.ZERO;
    	}
    	
    	return value;
    }

}
