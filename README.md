# Log Monitoring Extension  

##Use Case

Use for monitoring log files to report:

- the no of occurrences of each search term provided
- filesize

Typical usage is counting how many 'Warn' and/or 'Error' are logged. 

This extension works only with standalone machine agent.

Note: By default, the Machine agent can only send a fixed number of metrics to the controller. This extension can potentially report hundreds of metrics, so to change this limit, please follow the instructions mentioned [here](https://docs.appdynamics.com/display/PRO40/Metrics+Limits).

##Installation
1. Run 'mvn clean install' from log-monitoring-extension directory
2. Copy and unzip LogMonitor.zip from 'target' directory into \<machine_agent_dir\>/monitors/
3. Edit config.yaml file in LogMonitor/conf file and provide the required configuration (see Configuration section)
4. Restart the Machine Agent.

## Configuration

###config.yaml


| Param | Description |
| ----- | ----- |
| displayName | The display name of the log file. If not specified, logName is used by default. |
| logDirectory | The directory path where the log is located. |
| logName | The name of the log file, i.e. server.log. Supports wildcard character for filename that changes dynamically on rotation, e.g. server-*.log|
| searchStrings | The strings to search, e.g. "debug", "info", "error". Supports regex if matchExactString is set to false. Note, this is case insensitive regardless.|
| matchExactString | Allowed values: **true** or **false**. Set to true if you only want to match the exact string, otherwise set to false for regex support and contains in string. |
| ----- | ----- |
| noOfThreads | The no of threads used to process multiple logs concurrently |
| metricPrefix | The path prefix for viewing metrics in the metric browser. Default value is "Custom Metrics\|LogMonitor\|" |

Below is an example config with multiple log files to monitor, one of which uses the dynamic filename and search string regex support.

~~~~
logs:
  - displayName: "SimpleLog"
    logDirectory: "/var/log"
    logName: "server.log"
    searchStrings: ["debug", "info", "error"]
    matchExactString: true

  - displayName: "DynamicLog"
    logDirectory: "/var/log"
    logName: "server-*.log"
    searchStrings: ["deb\\w+g", "in\\w+o", "err\\w+r", ">", "<"]
    matchExactString: false
    
noOfThreads: 3

metricPrefix: "Custom Metrics|LogMonitor|"
~~~~

##Metric Path

Application Infrastructure Performance|\<Tier\>|Custom Metrics|LogMonitor|\<LogName\>|Search String|\<Search Term\>

Application Infrastructure Performance|\<Tier\>|Custom Metrics|LogMonitor|\<LogName\>|File size (Bytes)

##Contributing

Always feel free to fork and contribute any changes directly here on GitHub

##Community

Find out more in the [AppSphere](http://community.appdynamics.com/t5/eXchange-Community-AppDynamics/Log-Monitoring-Extension/idi-p/8830) community.


##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

