package com.appdynamics.extensions.logmonitor;

import static com.appdynamics.extensions.logmonitor.Constants.CONFIG_ARG;
import static com.appdynamics.extensions.logmonitor.Constants.DEFAULT_METRIC_PATH;
import static com.appdynamics.extensions.logmonitor.Constants.DEFAULT_NO_OF_THREADS;
import static com.appdynamics.extensions.logmonitor.Constants.METRIC_PATH_SEPARATOR;
import static com.appdynamics.extensions.logmonitor.Constants.THREAD_TIMEOUT;
import static com.appdynamics.extensions.logmonitor.config.LogConfigValidator.validate;
import static com.appdynamics.extensions.logmonitor.util.LogMonitorUtil.convertValueToZeroIfNullOrNegative;
import static com.appdynamics.extensions.logmonitor.util.LogMonitorUtil.resolvePath;
import static com.appdynamics.extensions.yml.YmlReader.readFromFile;

import com.appdynamics.extensions.logmonitor.config.Configuration;
import com.appdynamics.extensions.logmonitor.config.Log;
import com.appdynamics.extensions.logmonitor.processors.FilePointerProcessor;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Monitors the log file and counts the no of occurrences of the search terms provided
 *
 * @author Florencio Sarmiento
 */
public class LogMonitor extends AManagedMonitor {

    public static final Logger LOGGER = Logger.getLogger(LogMonitor.class);

    private volatile FilePointerProcessor filePointerProcessor;

    public LogMonitor() {
        LOGGER.info(String.format("Using Log Monitor Version [%s]",
                getImplementationVersion()));
        filePointerProcessor = new FilePointerProcessor();
    }

    public TaskOutput execute(Map<String, String> args,
                              TaskExecutionContext arg1) throws TaskExecutionException {
        LOGGER.info("Starting the Logger Monitoring task");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Args received were: %s", args));
        }

        if (args != null) {
            ExecutorService threadPool = null;

            try {
                String configFilename = resolvePath(args.get(CONFIG_ARG));
                Configuration config = readFromFile(configFilename, Configuration.class);
                List<Log> logs = getValidLogConfigs(config);

                if (!logs.isEmpty()) {
                    int noOfThreads = config.getNoOfThreads() > 0 ?
                            config.getNoOfThreads() : DEFAULT_NO_OF_THREADS;
                    threadPool = Executors.newFixedThreadPool(noOfThreads);

                    CompletionService<LogMetrics> logMonitorTasks =
                            createConcurrentTasks(threadPool, logs);

                    LogMetrics logMetrics = collectMetrics(logMonitorTasks, logs.size());
                    uploadMetrics(logMetrics, getMetricPrefix(config));

                    filePointerProcessor.updateFilePointerFile();

                    return new TaskOutput("Apache Log Monitoring task successfully completed");
                }

            } catch (Exception ex) {
                LOGGER.error("Unfortunately an issue has occurred: ", ex);

            } finally {
                if (threadPool != null && !threadPool.isShutdown()) {
                    threadPool.shutdown();
                }
            }
        }

        throw new TaskExecutionException("Log Monitoring task completed with failures.");
    }

    private CompletionService<LogMetrics> createConcurrentTasks(ExecutorService threadPool,
                                                                List<Log> logs) {

        CompletionService<LogMetrics> logMonitorTasks =
                new ExecutorCompletionService<LogMetrics>(threadPool);

        for (Log log : logs) {
            LogMonitorTask task = new LogMonitorTask(filePointerProcessor, log);
            logMonitorTasks.submit(task);
        }

        return logMonitorTasks;
    }

    private List<Log> getValidLogConfigs(Configuration config) {
        List<Log> validLogs = new ArrayList<Log>();

        for (Log log : config.getLogs()) {
            try {
                validate(log);
                validLogs.add(log);

            } catch (IllegalArgumentException ex) {
                LOGGER.error("Invalid config: " + log, ex);
            }
        }

        return validLogs;
    }

    private LogMetrics collectMetrics(CompletionService<LogMetrics> parallelTasks, int noOfTasks) {
        LogMetrics metrics = new LogMetrics();

        for (int i = 0; i < noOfTasks; i++) {
            try {
                LogMetrics collectedMetrics =
                        parallelTasks.take().get(THREAD_TIMEOUT, TimeUnit.SECONDS);
                metrics.addAll(collectedMetrics.getMetrics());

            } catch (InterruptedException e) {
                LOGGER.error("Task interrupted. ", e);

            } catch (ExecutionException e) {
                LOGGER.error("Task execution failed. ", e);

            } catch (TimeoutException e) {
                LOGGER.error("Task timed out. ", e);
            }
        }

        return metrics;
    }

    private String getMetricPrefix(Configuration config) {
        String metricPrefix = config.getMetricPrefix();

        if (StringUtils.isBlank(metricPrefix)) {
            metricPrefix = DEFAULT_METRIC_PATH;

        } else {
            metricPrefix = metricPrefix.trim();

            if (!metricPrefix.endsWith(METRIC_PATH_SEPARATOR)) {
                metricPrefix = metricPrefix + METRIC_PATH_SEPARATOR;
            }
        }

        return metricPrefix;
    }

    private void uploadMetrics(LogMetrics logMetrics, String metricPrefix) {
        for (Map.Entry<String, BigInteger> metric : logMetrics.getMetrics().entrySet()) {
            printCollectiveObservedCurrent(metricPrefix + metric.getKey(), metric.getValue());
        }
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

        BigInteger valueToReport = convertValueToZeroIfNullOrNegative(metricValue);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Sending [%s/%s/%s] metric = %s = %s => %s",
                    aggregation, timeRollup, cluster,
                    metricName, metricValue, valueToReport));
        }

        metricWriter.printMetric(valueToReport.toString());
    }

    public static String getImplementationVersion() {
        return LogMonitor.class.getPackage().getImplementationTitle();
    }

}
