package com.appdynamics.extensions.logmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import com.appdynamics.extensions.logmonitor.config.Log;
import com.appdynamics.extensions.logmonitor.config.SearchString;
import com.appdynamics.extensions.logmonitor.processors.FilePointer;
import com.appdynamics.extensions.logmonitor.processors.FilePointerProcessor;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class LogMonitorTaskTest {

    private LogMonitorTask classUnderTest;

    @Mock
    private FilePointerProcessor mockFilePointerProcessor;

    @Test
    public void testMatchExactStringIsTrue() throws Exception {
        Log log = new Log();
        log.setDisplayName("TestLog");
        log.setLogDirectory("src/test/resources/");
        log.setLogName("test-log-1.log");

        SearchString searchString = new SearchString();
        searchString.setCaseSensitive(false);
        searchString.setMatchExactString(true);
        searchString.setPattern("debug");
        searchString.setDisplayName("Debug");

        SearchString searchString1 = new SearchString();
        searchString1.setCaseSensitive(false);
        searchString1.setMatchExactString(true);
        searchString1.setPattern("info");
        searchString1.setDisplayName("Info");

        SearchString searchString2 = new SearchString();
        searchString2.setCaseSensitive(false);
        searchString2.setMatchExactString(true);
        searchString2.setPattern("error");
        searchString2.setDisplayName("Error");


        log.setSearchStrings(Lists.newArrayList(searchString, searchString1, searchString2));

        FilePointer filePointer = new FilePointer();
        filePointer.setFilename(log.getLogDirectory() + log.getLogName());
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString())).thenReturn(filePointer);

        classUnderTest = new LogMonitorTask(mockFilePointerProcessor, log);

        LogMetrics result = classUnderTest.call();
        assertEquals(log.getSearchStrings().size() + 1, result.getMetrics().size());

        assertEquals(13, result.getMetrics().get("TestLog|Search String|Debug|Debug").intValue());
        assertEquals(24, result.getMetrics().get("TestLog|Search String|Info|Info").intValue());
        assertEquals(7, result.getMetrics().get("TestLog|Search String|Error|Error").intValue());

        assertEquals(getFileSize(log.getLogDirectory(), log.getLogName()),
                result.getMetrics().get("TestLog|File size (Bytes)").intValue());
    }

    @Test
    public void testRegexSpecialChars() throws Exception {
        Log log = new Log();
        log.setDisplayName("TestLog");
        log.setLogDirectory("src/test/resources/");
        log.setLogName("test-log-regex.log");

        SearchString searchString = new SearchString();
        searchString.setCaseSensitive(false);
        searchString.setMatchExactString(false);
        searchString.setPattern("<");
        searchString.setDisplayName("Pattern <");

        SearchString searchString1 = new SearchString();
        searchString1.setCaseSensitive(false);
        searchString1.setMatchExactString(false);
        searchString1.setPattern(">");
        searchString1.setDisplayName("Pattern >");

        SearchString searchString2 = new SearchString();
        searchString2.setCaseSensitive(false);
        searchString2.setMatchExactString(false);
        searchString2.setPattern("\\*");
        searchString2.setDisplayName("Pattern *");

        SearchString searchString3 = new SearchString();
        searchString3.setCaseSensitive(false);
        searchString3.setMatchExactString(false);
        searchString3.setPattern("\\[");
        searchString3.setDisplayName("Pattern [");

        SearchString searchString4 = new SearchString();
        searchString4.setCaseSensitive(false);
        searchString4.setMatchExactString(false);
        searchString4.setPattern("\\]");
        searchString4.setDisplayName("Pattern ]");

        SearchString searchString5 = new SearchString();
        searchString5.setCaseSensitive(false);
        searchString5.setMatchExactString(false);
        searchString5.setPattern("\\.");
        searchString5.setDisplayName("Pattern .");

        log.setSearchStrings(Lists.newArrayList(searchString, searchString1, searchString2, searchString3, searchString4, searchString5));

        FilePointer filePointer = new FilePointer();
        filePointer.setFilename(log.getLogDirectory() + log.getLogName());
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString())).thenReturn(filePointer);

        classUnderTest = new LogMonitorTask(mockFilePointerProcessor, log);

        LogMetrics result = classUnderTest.call();
        assertEquals(log.getSearchStrings().size() + 1, result.getMetrics().size());

        assertEquals(5, result.getMetrics().get("TestLog|Search String|Pattern <|<").intValue());
        assertEquals(6, result.getMetrics().get("TestLog|Search String|Pattern >|>").intValue());
        assertEquals(16, result.getMetrics().get("TestLog|Search String|Pattern *|*").intValue());
        assertEquals(23, result.getMetrics().get("TestLog|Search String|Pattern [|[").intValue());
        assertEquals(23, result.getMetrics().get("TestLog|Search String|Pattern ]|]").intValue());
        assertEquals(2, result.getMetrics().get("TestLog|Search String|Pattern .|.").intValue());

        assertEquals(getFileSize(log.getLogDirectory(), log.getLogName()),
                result.getMetrics().get("TestLog|File size (Bytes)").intValue());
    }

    @Test
    public void testRegexWords() throws Exception {
        Log log = new Log();
        log.setDisplayName("TestLog");
        log.setLogDirectory("src/test/resources/");
        log.setLogName("test-log-regex.log");

        SearchString searchString = new SearchString();
        searchString.setCaseSensitive(false);
        searchString.setMatchExactString(false);
        searchString.setPattern("(\\s|^)m\\w+(\\s|$)");
        searchString.setDisplayName("Pattern start with M");

        SearchString searchString1 = new SearchString();
        searchString1.setCaseSensitive(false);
        searchString1.setMatchExactString(false);
        searchString1.setPattern("<\\w*>");
        searchString1.setDisplayName("Pattern start with <");

        SearchString searchString2 = new SearchString();
        searchString2.setCaseSensitive(false);
        searchString2.setMatchExactString(false);
        searchString2.setPattern("\\[JMX.*\\]");
        searchString2.setDisplayName("Pattern start with [JMX");

        log.setSearchStrings(Lists.newArrayList(searchString, searchString1, searchString2));

        FilePointer filePointer = new FilePointer();
        filePointer.setFilename(log.getLogDirectory() + log.getLogName());
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString())).thenReturn(filePointer);

        classUnderTest = new LogMonitorTask(mockFilePointerProcessor, log);

        LogMetrics result = classUnderTest.call();
        assertEquals(12, result.getMetrics().size());

        // matches (\\s|^)m\\w+(\\s|$)
        assertEquals(7, result.getMetrics().get("TestLog|Search String|Pattern start with M|Memorymetricgenerator").intValue());
        assertEquals(2, result.getMetrics().get("TestLog|Search String|Pattern start with M|Memory").intValue());
        assertEquals(2, result.getMetrics().get("TestLog|Search String|Pattern start with M|Major").intValue());
        assertEquals(1, result.getMetrics().get("TestLog|Search String|Pattern start with M|Mx").intValue());
        assertEquals(1, result.getMetrics().get("TestLog|Search String|Pattern start with M|Metric").intValue());
        assertEquals(2, result.getMetrics().get("TestLog|Search String|Pattern start with M|Minor").intValue());
        assertEquals(3, result.getMetrics().get("TestLog|Search String|Pattern start with M|Metrics").intValue());
        assertEquals(1, result.getMetrics().get("TestLog|Search String|Pattern start with M|Mbean").intValue());

        // matches <\\w*>
        assertEquals(2, result.getMetrics().get("TestLog|Search String|Pattern start with <|<this>").intValue());
        assertEquals(3, result.getMetrics().get("TestLog|Search String|Pattern start with <|<again>").intValue());

        // matches \\[JMX.*\\]
        assertEquals(1, result.getMetrics().get("TestLog|Search String|Pattern start with [JMX|[jmxservice]").intValue());

        assertEquals(getFileSize(log.getLogDirectory(), log.getLogName()),
                result.getMetrics().get("TestLog|File size (Bytes)").intValue());
    }

    @Test
    public void testLogFileUpdatedWithMoreLogs() throws Exception {
        String originalFilePath = this.getClass().getClassLoader().getResource("test-log-1.log").getPath();

        String testFilename = "active-test-log.log";
        String testFilepath = String.format("%s%s%s", getTargetDir().getPath(), File.separator, testFilename);
        copyFile(originalFilePath, testFilepath);

        Log log = new Log();
        log.setDisplayName("TestLog");
        log.setLogDirectory(getTargetDir().getPath());
        log.setLogName(testFilename);

        SearchString searchString = new SearchString();
        searchString.setCaseSensitive(false);
        searchString.setMatchExactString(true);
        searchString.setPattern("debug");
        searchString.setDisplayName("Debug");

        SearchString searchString1 = new SearchString();
        searchString1.setCaseSensitive(false);
        searchString1.setMatchExactString(true);
        searchString1.setPattern("info");
        searchString1.setDisplayName("Info");

        SearchString searchString2 = new SearchString();
        searchString2.setCaseSensitive(false);
        searchString2.setMatchExactString(true);
        searchString2.setPattern("error");
        searchString2.setDisplayName("Error");

        log.setSearchStrings(Lists.newArrayList(searchString, searchString1, searchString2));

        FilePointer filePointer = new FilePointer();
        filePointer.setFilename(log.getLogDirectory() + File.separator + log.getLogName());
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString())).thenReturn(filePointer);

        classUnderTest = new LogMonitorTask(mockFilePointerProcessor, log);

        LogMetrics result = classUnderTest.call();
        assertEquals(4, result.getMetrics().size());

        assertEquals(13, result.getMetrics().get("TestLog|Search String|Debug|Debug").intValue());
        assertEquals(24, result.getMetrics().get("TestLog|Search String|Info|Info").intValue());
        assertEquals(7, result.getMetrics().get("TestLog|Search String|Error|Error").intValue());

        long filesize = getFileSize(log.getLogDirectory(), log.getLogName());
        assertEquals(filesize, result.getMetrics().get("TestLog|File size (Bytes)").intValue());

        // simulate our filepointer was updated
        filePointer.updateLastReadPosition(filesize);
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString()))
                .thenReturn(filePointer);

        // perform the update
        List<String> logsToAdd = Arrays.asList("",
                new Date() + "	DEBUG	This is the first line",
                new Date() + "	INFO	This is the second line",
                new Date() + "	INFO	This is the third line",
                new Date() + "	DEBUG	This is the fourth line",
                new Date() + "	DEBUG	This is the fifth line");

        updateLogFile(testFilepath, logsToAdd, true);

        result = classUnderTest.call();
        assertEquals(3, result.getMetrics().size());

        assertEquals(3, result.getMetrics().get("TestLog|Search String|Debug|Debug").intValue());
        assertEquals(2, result.getMetrics().get("TestLog|Search String|Info|Info").intValue());
    }

    @Test
    public void testLogFileRotated() throws Exception {
        String originalFilePath = this.getClass().getClassLoader().getResource("test-log-2.log").getPath();

        String testFilename = "rotated-test-log.log";
        String testFilepath = String.format("%s%s%s", getTargetDir().getPath(), File.separator, testFilename);
        copyFile(originalFilePath, testFilepath);

        Log log = new Log();
        log.setDisplayName("TestLog");
        log.setLogDirectory(getTargetDir().getPath());
        log.setLogName(testFilename);

        SearchString searchString = new SearchString();
        searchString.setCaseSensitive(false);
        searchString.setMatchExactString(true);
        searchString.setPattern("trace");
        searchString.setDisplayName("Trace");

        log.setSearchStrings(Lists.newArrayList(searchString));

        FilePointer filePointer = new FilePointer();
        filePointer.setFilename(log.getLogDirectory() + File.separator + log.getLogName());
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString())).thenReturn(filePointer);

        classUnderTest = new LogMonitorTask(mockFilePointerProcessor, log);

        LogMetrics result = classUnderTest.call();
        assertEquals(2, result.getMetrics().size());

        assertEquals(10, result.getMetrics().get("TestLog|Search String|Trace|Trace").intValue());

        long fileSizeBeforeRotation = getFileSize(log.getLogDirectory(), log.getLogName());
        assertEquals(fileSizeBeforeRotation,
                result.getMetrics().get("TestLog|File size (Bytes)").intValue());

        // simulate our filepointer was updated
        filePointer.updateLastReadPosition(fileSizeBeforeRotation);
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString()))
                .thenReturn(filePointer);

        // rotate log with these strings
        List<String> logsToAdd = Arrays.asList("",
                new Date() + "	TRACE	This is the first line",
                new Date() + "	INFO	This is the second line",
                new Date() + "	TRACE	This is the third line",
                new Date() + "	DEBUG	This is the fourth line",
                new Date() + "	DEBUG	This is the fifth line");

        updateLogFile(testFilepath, logsToAdd, false);

        result = classUnderTest.call();
        assertEquals(2, result.getMetrics().size());

        assertEquals(2, result.getMetrics().get("TestLog|Search String|Trace|Trace").intValue());

        long fileSizeAfterRotation = getFileSize(log.getLogDirectory(), log.getLogName());
        assertEquals(fileSizeAfterRotation,
                result.getMetrics().get("TestLog|File size (Bytes)").intValue());

        assertTrue("Rotated log should've been smaller", fileSizeAfterRotation < fileSizeBeforeRotation);
    }

    @Test
    public void testDynamicLogFileName() throws Exception {
        String dynamicLog1 = this.getClass().getClassLoader().getResource("dynamic-log-1.log").getPath();

        String testFilename = "active-dynamic-log-1.log";
        String testFilepath = String.format("%s%s%s", getTargetDir().getPath(), File.separator, testFilename);
        copyFile(dynamicLog1, testFilepath);

        Log log = new Log();
        log.setLogDirectory(getTargetDir().getPath());
        log.setLogName("active-dynamic-*");

        SearchString searchString = new SearchString();
        searchString.setCaseSensitive(false);
        searchString.setMatchExactString(true);
        searchString.setPattern("debug");
        searchString.setDisplayName("Debug");

        SearchString searchString1 = new SearchString();
        searchString1.setCaseSensitive(false);
        searchString1.setMatchExactString(true);
        searchString1.setPattern("error");
        searchString1.setDisplayName("Error");

        log.setSearchStrings(Lists.newArrayList(searchString, searchString1));

        FilePointer filePointer = new FilePointer();
        filePointer.setFilename(log.getLogDirectory() + File.separator + testFilename);
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString())).thenReturn(filePointer);

        classUnderTest = new LogMonitorTask(mockFilePointerProcessor, log);

        LogMetrics result = classUnderTest.call();
        assertEquals(2, result.getMetrics().size());

        assertEquals(3, result.getMetrics().get("active-dynamic-*|Search String|Debug|Debug").intValue());

        long filesize = getFileSize(log.getLogDirectory(), testFilename);
        assertEquals(filesize, result.getMetrics().get("active-dynamic-*|File size (Bytes)").intValue());

        // simulate our filepointer was updated
        filePointer.updateLastReadPosition(filesize);
        when(mockFilePointerProcessor.getFilePointer(anyString(), anyString()))
                .thenReturn(filePointer);

        // simulate new file created with different name
        Thread.sleep(1000);
        String dynamicLog2 = this.getClass().getClassLoader().getResource("dynamic-log-2.log").getPath();

        testFilename = "active-dynamic-log-2.log";
        testFilepath = String.format("%s%s%s", getTargetDir().getPath(), File.separator, testFilename);
        copyFile(dynamicLog2, testFilepath);

        result = classUnderTest.call();
        assertEquals(2, result.getMetrics().size());

        assertEquals(7, result.getMetrics().get("active-dynamic-*|Search String|Error|Error").intValue());

        filesize = getFileSize(log.getLogDirectory(), testFilename);
        assertEquals(filesize, result.getMetrics().get("active-dynamic-*|File size (Bytes)").intValue());
    }

    private long getFileSize(String logDir, String logName) throws Exception {
        String fullPath = String.format("%s%s%s", logDir, File.separator, logName);
        RandomAccessFile file = new RandomAccessFile(fullPath, "r");
        long fileSize = file.length();
        file.close();
        return fileSize;
    }

    private void copyFile(String sourceFilePath, String destFilePath) throws Exception {
        FileChannel sourceChannel = null;
        FileChannel destChannel = null;

        try {
            sourceChannel = new FileInputStream(new File(sourceFilePath)).getChannel();
            destChannel = new FileOutputStream(new File(destFilePath)).getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());

        } finally {
            sourceChannel.close();
            destChannel.close();
        }
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

    private File getTargetDir() {
        return new File("./target");
    }

}
