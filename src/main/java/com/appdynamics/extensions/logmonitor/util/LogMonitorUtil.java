package com.appdynamics.extensions.logmonitor.util;

import com.appdynamics.extensions.PathResolver;
import com.appdynamics.extensions.logmonitor.SearchPattern;
import com.appdynamics.extensions.logmonitor.config.SearchString;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import org.apache.commons.lang.StringUtils;
import org.bitbucket.kienerj.OptimizedRandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Florencio Sarmiento
 */
public class LogMonitorUtil {

    private static final String CASE_SENSITIVE_PATTERN = "(?-i)";
    private static final String CASE_INSENSITIVE_PATTERN = "(?i)";

    public static String resolvePath(String filename) {
        if (StringUtils.isBlank(filename)) {
            return "";
        }

        //for absolute paths
        if (new File(filename).exists()) {
            return filename;
        }

        //for relative paths
        File jarPath = PathResolver.resolveDirectory(AManagedMonitor.class);
        String configFileName = String.format("%s%s%s", jarPath, File.separator, filename);
        return configFileName;
    }


    public static List<SearchPattern> createPattern(List<SearchString> searchStrings) {
        List<SearchPattern> searchPatterns = new ArrayList<SearchPattern>();
        if (searchStrings != null && !searchStrings.isEmpty()) {

            for (SearchString searchString : searchStrings) {
                Pattern pattern = null;

                StringBuilder rawPatternsStringBuilder = new StringBuilder();

                if (searchString.getCaseSensitive()) {
                    rawPatternsStringBuilder.append(CASE_SENSITIVE_PATTERN);
                } else {
                    rawPatternsStringBuilder.append(CASE_INSENSITIVE_PATTERN);
                }

                if (searchString.getMatchExactString()) {

                    rawPatternsStringBuilder.append("(?<=\\s|^)");
                    rawPatternsStringBuilder.append(Pattern.quote(searchString.getPattern().trim()));
                    rawPatternsStringBuilder.append("(?=\\s|$)");

                } else {

                    rawPatternsStringBuilder.append(searchString.getPattern().trim());
                }

                pattern = Pattern.compile(rawPatternsStringBuilder.toString());

                SearchPattern searchPattern = new SearchPattern(searchString.getDisplayName(), pattern, searchString.getCaseSensitive());
                searchPatterns.add(searchPattern);
            }

        }

        return searchPatterns;
    }

    public static void closeRandomAccessFile(OptimizedRandomAccessFile randomAccessFile) {
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e) {
            }
        }
    }

    public static BigInteger convertValueToZeroIfNullOrNegative(BigInteger value) {
        if (value == null || value.compareTo(BigInteger.ZERO) < 0) {
            return BigInteger.ZERO;
        }

        return value;
    }

}
