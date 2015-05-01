package com.appdynamics.extensions.logmonitor.config;

import java.util.Set;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * @author Florencio Sarmiento
 *
 */
public class Log {

	private String displayName;

	private String logDirectory;

	private String logName;

	private Set<String> searchStrings;

	private Boolean matchExactString;

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getLogDirectory() {
		return logDirectory;
	}

	public void setLogDirectory(String logDirectory) {
		this.logDirectory = logDirectory;
	}

	public String getLogName() {
		return logName;
	}

	public void setLogName(String logName) {
		this.logName = logName;
	}

	public Set<String> getSearchStrings() {
		return searchStrings;
	}

	public void setSearchStrings(Set<String> searchStrings) {
		this.searchStrings = searchStrings;
	}

	public Boolean getMatchExactString() {
		return this.matchExactString != null ?
				this.matchExactString : Boolean.FALSE;
	}

	public void setMatchExactString(Boolean matchExactString) {
		this.matchExactString = matchExactString;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this,
				ToStringStyle.SHORT_PREFIX_STYLE);
	}

}
