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
@XmlType(propOrder = { "method", "path", "queryParameters", "cookieParameters", "sessionParameters", "headerParameters", "responseHeaders", "roles", "permissionContext", "permissionAction", "preferredResponseType",
		"asynchronous", "inputAsStream", "outputAsStream", "input", "output", "sanitizeInput", "acceptedLanguages", "configurationType", "device", "token", "lenient", "namingConvention", "webApplicationId", "geoPosition", 
		"language", "allowFormBinding", "caseInsensitive", "cache", "allowCookiesWithoutReferer", "allowCookiesWithExternalReferer", "request", "allowHeaderAsQueryParameter", "useServerCache", "source", 
		"allowRaw", "domain", "temporaryAlias", "temporarySecret", "temporaryCorrelationId", "rateLimitContext", "rateLimitAction" })
public class RESTInterfaceConfiguration {

	private DefinedType input, output;
	private String path, queryParameters, cookieParameters, sessionParameters, headerParameters, responseHeaders;
	private String permissionContext, permissionAction;
	private String rateLimitContext, rateLimitAction;
	private String temporaryAlias, temporarySecret, temporaryCorrelationId;
	private WebMethod method;
	private List<String> roles;
	private Boolean asynchronous;
	private WebResponseType preferredResponseType;
	private Boolean inputAsStream, outputAsStream;
	private Boolean sanitizeInput;
	private Boolean acceptedLanguages;
	private Boolean device, token;
	private boolean language;
	private boolean lenient, allowRaw;
	private boolean webApplicationId, geoPosition;
	private NamingConvention namingConvention;
	private boolean allowFormBinding;
	private boolean caseInsensitive;
	private boolean cache, useServerCache;
	private boolean request, source, domain;
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
	
	@Advanced
	@Comment(title = "Whether the JSON should be encoded or allow for raw unencoded content")
	public boolean isAllowRaw() {
		return allowRaw;
	}
	public void setAllowRaw(boolean allowRaw) {
		this.allowRaw = allowRaw;
	}
	
	@Advanced
	@Comment(title = "Inject the domain the request was done on, this can be useful for differentiating logic depending on the source domain")
	public boolean isDomain() {
		return domain;
	}
	public void setDomain(boolean domain) {
		this.domain = domain;
	}
	public Boolean getToken() {
		return token;
	}
	public void setToken(Boolean token) {
		this.token = token;
	}
	
	@Advanced
	public String getTemporaryAlias() {
		return temporaryAlias;
	}
	public void setTemporaryAlias(String temporaryAlias) {
		this.temporaryAlias = temporaryAlias;
	}

	@Advanced
	public String getTemporarySecret() {
		return temporarySecret;
	}
	public void setTemporarySecret(String temporarySecret) {
		this.temporarySecret = temporarySecret;
	}
	
	@Comment(title = "You can correlate a temporary authentication to something to restrict it further. For example you don't have permission to download _any_ file once, just that _specific_ file")
	@Advanced
	public String getTemporaryCorrelationId() {
		return temporaryCorrelationId;
	}
	public void setTemporaryCorrelationId(String temporaryCorrelationId) {
		this.temporaryCorrelationId = temporaryCorrelationId;
	}
	
	@Comment(title = "The context to use for rate limiting, the context is left empty if not filled in")
	@Advanced
	public String getRateLimitContext() {
		return rateLimitContext;
	}
	public void setRateLimitContext(String rateLimitContext) {
		this.rateLimitContext = rateLimitContext;
	}
	
	@Comment(title = "The action to use for rate limiting, by default the service id will be used as action")
	@Advanced
	public String getRateLimitAction() {
		return rateLimitAction;
	}
	public void setRateLimitAction(String rateLimitAction) {
		this.rateLimitAction = rateLimitAction;
	}
	
	@Comment(title = "Whether or not we want to expose a geo position header (if available)")
	@Advanced
	public boolean isGeoPosition() {
		return geoPosition;
	}
	public void setGeoPosition(boolean geoPosition) {
		this.geoPosition = geoPosition;
	}

}
