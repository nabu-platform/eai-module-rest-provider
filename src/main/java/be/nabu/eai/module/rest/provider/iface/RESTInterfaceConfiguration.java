package be.nabu.eai.module.rest.provider.iface;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.Advanced;
import be.nabu.eai.api.Comment;
import be.nabu.eai.api.Hidden;
import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.module.rest.WebMethod;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "restInterface")
@XmlType(propOrder = { "method", "path", "queryParameters", "cookieParameters", "sessionParameters", "headerParameters", "responseHeaders", "roles", "permissionContext", "permissionAction", "preferredResponseType", "asynchronous", "inputAsStream", "outputAsStream", "input", "output", "sanitizeInput", "acceptedLanguages", "configurationType", "device", "lenient", "namingConvention", "webApplicationId", "language", "allowFormBinding", "caseInsensitive", "cache", "allowCookiesWithoutReferer", "allowCookiesWithExternalReferer", "request", "allowHeaderAsQueryParameter", "useServerCache", "source" })
public class RESTInterfaceConfiguration {

	private DefinedType input, output;
	private String path, queryParameters, cookieParameters, sessionParameters, headerParameters, responseHeaders;
	private String permissionContext, permissionAction;
	private WebMethod method;
	private List<String> roles;
	private Boolean asynchronous;
	private WebResponseType preferredResponseType;
	private Boolean inputAsStream, outputAsStream;
	private Boolean sanitizeInput;
	private Boolean acceptedLanguages;
	private Boolean device;
	private boolean language;
	private boolean lenient;
	private boolean webApplicationId;
	private NamingConvention namingConvention;
	private boolean allowFormBinding;
	private boolean caseInsensitive;
	private boolean cache, useServerCache;
	private boolean request, source;
	// allow cookies to be used if there is no referer
	// specifically IE does not send a referer when window.open is used
	// this can potentially be an issue when downloading files via a REST service
	private boolean allowCookiesWithoutReferer;
	// add for completeness sake
	private boolean allowCookiesWithExternalReferer;
	
	// you can allow headers to be put in the query parameter list
	// this is specifically done to circumvent restrictions in html 4 and previous where it is nearly impossible to do a clean ajax-based download
	// in html 5 you can serialize the data and download it using an injected "a" tag with download attribute, but IE support is lacking in this regard (and edge only since build 14)
	// note that this can also be a nifty feature when simply sending a link to someone as you have no control over the headers they send to retrieve it
	private boolean allowHeaderAsQueryParameter;
	
	private DefinedType configurationType;

	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	@XmlElement(name = "role")
	public List<String> getRoles() {
		return roles;
	}
	public void setRoles(List<String> roles) {
		this.roles = roles;
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
	public Boolean getSanitizeInput() {
		return sanitizeInput;
	}
	public void setSanitizeInput(Boolean sanitizeInput) {
		this.sanitizeInput = sanitizeInput;
	}
	// will be removed at the end of 2018
	@Deprecated
	public Boolean getAcceptedLanguages() {
		return acceptedLanguages;
	}
	public void setAcceptedLanguages(Boolean acceptedLanguages) {
		this.acceptedLanguages = acceptedLanguages;
	}
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getConfigurationType() {
		return configurationType;
	}
	public void setConfigurationType(DefinedType configurationType) {
		this.configurationType = configurationType;
	}
	public String getPermissionContext() {
		return permissionContext;
	}
	public void setPermissionContext(String permissionContext) {
		this.permissionContext = permissionContext;
	}
	public String getPermissionAction() {
		return permissionAction;
	}
	public void setPermissionAction(String permissionAction) {
		this.permissionAction = permissionAction;
	}
	public Boolean getDevice() {
		return device;
	}
	public void setDevice(Boolean device) {
		this.device = device;
	}

	public boolean getLenient() {
		return lenient;
	}
	public void setLenient(boolean lenient) {
		this.lenient = lenient;
	}
	
	@Advanced
	@Comment(title = "Setting this parameter will make sure the correct naming convention is exposed to the outside world")
	public NamingConvention getNamingConvention() {
		return namingConvention;
	}
	public void setNamingConvention(NamingConvention namingConvention) {
		this.namingConvention = namingConvention;
	}
	
	@Advanced
	@Comment(title = "If set to true, the web application id will be injected into the rest service")
	public boolean isWebApplicationId() {
		return webApplicationId;
	}
	public void setWebApplicationId(boolean webApplicationId) {
		this.webApplicationId = webApplicationId;
	}
	
	public boolean isLanguage() {
		return language;
	}
	public void setLanguage(boolean language) {
		this.language = language;
	}
	
	@Advanced
	@Comment(title = "Whether or not form binding is allowed")
	public boolean isAllowFormBinding() {
		return allowFormBinding;
	}
	public void setAllowFormBinding(boolean allowFormBinding) {
		this.allowFormBinding = allowFormBinding;
	}
	
	@Advanced
	@Comment(title = "Whether the urls are matched case sensitive (default) or not")
	public boolean isCaseInsensitive() {
		return caseInsensitive;
	}
	public void setCaseInsensitive(boolean caseInsensitive) {
		this.caseInsensitive = caseInsensitive;
	}
	
	@Hidden
	public boolean isCache() {
		return cache;
	}
	public void setCache(boolean cache) {
		this.cache = cache;
	}
	
	@Advanced
	public boolean isAllowCookiesWithoutReferer() {
		return allowCookiesWithoutReferer;
	}
	public void setAllowCookiesWithoutReferer(boolean allowCookiesWithoutReferer) {
		this.allowCookiesWithoutReferer = allowCookiesWithoutReferer;
	}
	
	@Advanced
	public boolean isAllowCookiesWithExternalReferer() {
		return allowCookiesWithExternalReferer;
	}
	public void setAllowCookiesWithExternalReferer(boolean allowCookiesWithExternalReferer) {
		this.allowCookiesWithExternalReferer = allowCookiesWithExternalReferer;
	}
	
	@Advanced
	public boolean isRequest() {
		return request;
	}
	public void setRequest(boolean includeRequest) {
		this.request = includeRequest;
	}
	
	@Advanced
	public boolean isAllowHeaderAsQueryParameter() {
		return allowHeaderAsQueryParameter;
	}
	public void setAllowHeaderAsQueryParameter(boolean allowHeaderAsQueryParameter) {
		this.allowHeaderAsQueryParameter = allowHeaderAsQueryParameter;
	}
	
	@Advanced
	@Comment(title = "Whether or not to use the server cache (if available) to fill in etag and/or last modified headers and validate them against it")
	public boolean isUseServerCache() {
		return useServerCache;
	}
	public void setUseServerCache(boolean useServerCache) {
		this.useServerCache = useServerCache;
	}
	
	@Advanced
	public boolean isSource() {
		return source;
	}
	public void setSource(boolean source) {
		this.source = source;
	}
	
}
