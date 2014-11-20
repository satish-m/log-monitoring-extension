# Log Monitoring Extension  

##Use Case

Use for monitoring log files to report:

- the no of occurrences of each search term provided
- filesize

Typical usage is counting how many 'Warn' and/or 'Error' are logged. 

This extension works only with standalone machine agent.

##Installation
1. Run 'mvn clean install' from log-monitoring-extension directory
2. Copy and unzip LogMonitor.zip from 'target' directory into \<machine_agent_dir\>/monitors/
3. Edit monitor.xml file and provide the min required values for: 
	- file.path.1 
	- search.strings.for.file.1
	- match.exact.string.in.file.1 
	- filename.alias.for.file.1	
4. Restart the Machine Agent.

## monitor.xml


| Param | Description |
| ----- | ----- |
| file.path.1 | The full path of the log file, e.g. /var/log/test.log  |
| search.strings.for.file.1 | The search strings. For multiple search strings, use comma as delimiter, e.g. debug, info, error |
| match.exact.string.in.file.1 | Allowed values: true or false - if set to true, it only matches the exact string defined, and not contains in string, e.g. search string = 404 will be matched in 404, http 404 and not in $404, 5404, 4040392, etc |
| filename.alias.for.file.1 | The display name of the log file. If not specified, log filename is used by default. |

**Note: To monitor multiple log files, add a new set of arguments with incremented index for each log file, see example monitor.xml below:**

~~~~
<monitor>
    <name>log-monitor</name>
    <type>managed</type>
    <description>Log Monitoring Extension</description>
    <monitor-configuration></monitor-configuration>
    <monitor-run-task>
        <execution-style>periodic</execution-style>
        <execution-frequency-in-seconds>60</execution-frequency-in-seconds>
        <name>Log Monitor Run task</name>
        <display-name>Log Monitor Run task</display-name>
        <description>Log Monitor Run task</description>
        <type>java</type>
        <execution-timeout-in-secs>180</execution-timeout-in-secs>
        
        <task-arguments>
        	<!-- This is the first log to monitor -->
            <argument name="file.path.1" is-required="true" default-value="/var/log/test.log"/>
            <argument name="search.strings.for.file.1" is-required="true" default-value="debug, info, warn, error"/>
            <argument name="match.exact.string.in.file.1" is-required="false" default-value="true"/>
            <argument name="filename.alias.for.file.1" is-required="false" default-value="TestLog"/>
            
            <!-- This is the second log file to monitor -->
            <argument name="file.path.2" is-required="true" default-value="/var/log/test2.log"/>
            <argument name="search.strings.for.file.2" is-required="true" default-value="error"/>
            <argument name="match.exact.string.in.file.2" is-required="false" default-value="false"/>
            <argument name="filename.alias.for.file.2" is-required="false" default-value="AnotherLog"/>            
        </task-arguments>
        
        <java-task>
            <classpath>log-monitoring-extension.jar</classpath>
            <impl-class>com.appdynamics.extensions.logmonitor.LogMonitor</impl-class>
        </java-task>
        
    </monitor-run-task>
</monitor>
~~~~

##Metric Path

Application Infrastructure Performance|\<Tier\>|Custom Metrics|LogMonitor|\<LogName\>|Search String|\<Search Term\>

Application Infrastructure Performance|\<Tier\>|Custom Metrics|LogMonitor|\<LogName\>|File size (Bytes)

##Contributing

Always feel free to fork and contribute any changes directly here on GitHub

##Community



##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

