/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.rest.provider;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.rest.WebMethod;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "webRestArtifact")
@XmlType(propOrder = { "method", "path", "queryParameters", "cookieParameters", "sessionParameters", "headerParameters", "responseHeaders", "role", "preferredResponseType", "asynchronous", "inputAsStream", "outputAsStream", "input", "output", "acceptedLanguages" })
public class RESTServiceConfiguration {

	private DefinedType input, output;
	private String path, queryParameters, cookieParameters, sessionParameters, headerParameters, responseHeaders;
	private WebMethod method;
	private String role;
	private Boolean asynchronous;
	private WebResponseType preferredResponseType;
	private Boolean inputAsStream, outputAsStream;
	private Boolean acceptedLanguages;
	
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}

	public String getRole() {
		return role;
	}
	public void setRole(String role) {
		this.role = role;
	}
	public String getQueryParameters() {
		return queryParameters;
	}
	public void setQueryParameters(String queryParameters) {
		this.queryParameters = queryParameters;
	}
	public String getCookieParameters() {
		return cookieParameters;
	}
	public void setCookieParameters(String cookieParameters) {
		this.cookieParameters = cookieParameters;
	}
	public String getSessionParameters() {
		return sessionParameters;
	}
	public void setSessionParameters(String sessionParameters) {
		this.sessionParameters = sessionParameters;
	}
	public String getHeaderParameters() {
		return headerParameters;
	}
	public void setHeaderParameters(String headerParameters) {
		this.headerParameters = headerParameters;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getInput() {
		return input;
	}
	public void setInput(DefinedType input) {
		this.input = input;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getOutput() {
		return output;
	}
	public void setOutput(DefinedType output) {
		this.output = output;
	}
	@Deprecated
	public Boolean getAsynchronous() {
		return asynchronous;
	}
	public void setAsynchronous(Boolean asynchronous) {
		this.asynchronous = asynchronous;
	}
	public WebResponseType getPreferredResponseType() {
		return preferredResponseType;
	}
	public void setPreferredResponseType(WebResponseType preferredResponseType) {
		this.preferredResponseType = preferredResponseType;
	}
	public WebMethod getMethod() {
		return method;
	}
	public void setMethod(WebMethod method) {
		this.method = method;
	}
	public Boolean getInputAsStream() {
		return inputAsStream;
	}
	public void setInputAsStream(Boolean inputAsStream) {
		this.inputAsStream = inputAsStream;
	}
	public Boolean getOutputAsStream() {
		return outputAsStream;
	}
	public void setOutputAsStream(Boolean outputAsStream) {
		this.outputAsStream = outputAsStream;
	}
	public String getResponseHeaders() {
		return responseHeaders;
	}
	public void setResponseHeaders(String responseHeaders) {
		this.responseHeaders = responseHeaders;
	}
	public Boolean getAcceptedLanguages() {
		return acceptedLanguages;
	}
	public void setAcceptedLanguages(Boolean acceptedLanguages) {
		this.acceptedLanguages = acceptedLanguages;
	}
	
}
