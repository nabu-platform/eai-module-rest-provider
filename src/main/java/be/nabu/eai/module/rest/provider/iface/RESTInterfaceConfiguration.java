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
import be.nabu.libs.types.api.annotation.Field;

@XmlRootElement(name = "restInterface")
@XmlType(propOrder = { "method", "path", "queryParameters", "cookieParameters", "sessionParameters", "headerParameters", "responseHeaders", "roles", "permissionContext", "permissionAction", "preferredResponseType",
		"asynchronous", "inputAsStream", "outputAsStream", "input", "output", "sanitizeInput", "acceptedLanguages", "configurationType", "device", "token", "lenient", "namingConvention", "webApplicationId", "geoPosition", 
		"language", "allowFormBinding", "caseInsensitive", "cache", "allowCookiesWithoutReferer", "allowCookiesWithExternalReferer", "request", "allowHeaderAsQueryParameter", "useServerCache", "source", 
		"allowRaw", "domain", "temporaryAlias", "temporarySecret", "temporaryCorrelationId", "rateLimitContext", "rateLimitAction", "ignoreOffline", "allowRootArrays" })
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
	private boolean lenient = true, allowRaw;
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
	// whether or not we want to ignore offline modus
	private boolean ignoreOffline;
	
	// you can allow headers to be put in the query parameter list
	// this is specifically done to circumvent restrictions in html 4 and previous where it is nearly impossible to do a clean ajax-based download
	// in html 5 you can serialize the data and download it using an injected "a" tag with download attribute, but IE support is lacking in this regard (and edge only since build 14)
	// note that this can also be a nifty feature when simply sending a link to someone as you have no control over the headers they send to retrieve it
	private boolean allowHeaderAsQueryParameter;
	
	// in JSON you can allow arrays at the root of your object...
	private boolean allowRootArrays;
	
	private DefinedType configurationType;

	@Field(comment = "The path this REST service should listen on, use {} to indicate variable parts. For example: /resource/{resourceId}")
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	
	@Field(group = "security")
	@XmlElement(name = "role")
	public List<String> getRoles() {
		return roles;
	}
	public void setRoles(List<String> roles) {
		this.roles = roles;
	}
	@Field(comment = "A comma-separated list of query input parameters. You can further limit the resulting type in the 'Type Definitions' section.")
	public String getQueryParameters() {
		return queryParameters;
	}
	public void setQueryParameters(String queryParameters) {
		this.queryParameters = queryParameters;
	}
	@Field(comment = "A comma-separated list of cookie input parameters. You can further limit the resulting type in the 'Type Definitions' section.")
	public String getCookieParameters() {
		return cookieParameters;
	}
	public void setCookieParameters(String cookieParameters) {
		this.cookieParameters = cookieParameters;
	}
	@Field(comment = "A comma-separated list of session input parameters. You can further limit the resulting type in the 'Type Definitions' section.")
	public String getSessionParameters() {
		return sessionParameters;
	}
	public void setSessionParameters(String sessionParameters) {
		this.sessionParameters = sessionParameters;
	}
	@Field(comment = "A comma-separated list of header input parameters. You can further limit the resulting type in the 'Type Definitions' section.")
	public String getHeaderParameters() {
		return headerParameters;
	}
	public void setHeaderParameters(String headerParameters) {
		this.headerParameters = headerParameters;
	}
	
	@Field(show = "inputAsStream != true", comment = "Configure the definition of the input type. The incoming request will be automatically parsed.")
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getInput() {
		return input;
	}
	public void setInput(DefinedType input) {
		this.input = input;
	}
	
	@Field(show = "outputAsStream != true", comment = "Configure the definition of the output type. The outgoing response will be automatically formatted.")
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
	@Advanced
	@Field(comment = "If the requesting party does not indicate their preferred response type, what should be the default?")
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
	@Field(show = "input == null", comment = "Toggle this if you want to receive the input as pure bytes rather than a structured object. This is especially useful for receiving binary files.")
	public Boolean getInputAsStream() {
		return inputAsStream;
	}
	public void setInputAsStream(Boolean inputAsStream) {
		this.inputAsStream = inputAsStream;
	}
	@Field(show = "output == null", comment = "Toggle this if you want to respond with pure bytes rather than a structure object. This is especially useful when sending binary files.")
	public Boolean getOutputAsStream() {
		return outputAsStream;
	}
	public void setOutputAsStream(Boolean outputAsStream) {
		this.outputAsStream = outputAsStream;
	}
	@Field(comment = "A comma-separated list of response output headers you want to set. You can further limit the resulting type in the 'Type Definitions' section.")
	public String getResponseHeaders() {
		return responseHeaders;
	}
	public void setResponseHeaders(String responseHeaders) {
		this.responseHeaders = responseHeaders;
	}
	@Advanced
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
	@Field(group = "enrichInput")
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getConfigurationType() {
		return configurationType;
	}
	public void setConfigurationType(DefinedType configurationType) {
		this.configurationType = configurationType;
	}
	@Field(group = "security")
	public String getPermissionContext() {
		return permissionContext;
	}
	public void setPermissionContext(String permissionContext) {
		this.permissionContext = permissionContext;
	}
	@Field(group = "security")
	public String getPermissionAction() {
		return permissionAction;
	}
	public void setPermissionAction(String permissionAction) {
		this.permissionAction = permissionAction;
	}
	@Field(group = "enrichInput")
	public Boolean getDevice() {
		return device;
	}
	public void setDevice(Boolean device) {
		this.device = device;
	}

	@Field(comment = "When lenient is enabled, input that is (partially) invalid will not always trigger exceptions.")
	@Advanced
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
	
	@Field(group = "enrichInput", comment = "If set to true, the web application id will be injected into the rest service")
	public boolean isWebApplicationId() {
		return webApplicationId;
	}
	public void setWebApplicationId(boolean webApplicationId) {
		this.webApplicationId = webApplicationId;
	}
	
	@Field(group = "enrichInput")
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
	
	@Comment(title = "If you want to create a URL to download the data as a specific type (e.g. excel), you can't manipulate the headers directly to indicate content type, language... By enabling this, you can set a select few headers as a query parameter, specifically 'header:Accept', 'header:Accept-Language' and 'header:'Accept-Content-Disposition'")
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
	
	@Field(group = "enrichInput")
	@Comment(title = "Inject the domain the request was done on, this can be useful for differentiating logic depending on the source domain")
	public boolean isDomain() {
		return domain;
	}
	public void setDomain(boolean domain) {
		this.domain = domain;
	}
	
	@Field(group = "enrichInput")
	public Boolean getToken() {
		return token;
	}
	public void setToken(Boolean token) {
		this.token = token;
	}
	
	@Field(group = "security")
	public String getTemporaryAlias() {
		return temporaryAlias;
	}
	public void setTemporaryAlias(String temporaryAlias) {
		this.temporaryAlias = temporaryAlias;
	}

	@Field(group = "security")
	public String getTemporarySecret() {
		return temporarySecret;
	}
	public void setTemporarySecret(String temporarySecret) {
		this.temporarySecret = temporarySecret;
	}
	
	@Comment(title = "You can correlate a temporary authentication to something to restrict it further. For example you don't have permission to download _any_ file once, just that _specific_ file")
	@Field(group = "security")
	public String getTemporaryCorrelationId() {
		return temporaryCorrelationId;
	}
	public void setTemporaryCorrelationId(String temporaryCorrelationId) {
		this.temporaryCorrelationId = temporaryCorrelationId;
	}
	
	@Field(group = "rateLimiting", comment = "The context to use for rate limiting, the context is left empty if not filled in")
	public String getRateLimitContext() {
		return rateLimitContext;
	}
	public void setRateLimitContext(String rateLimitContext) {
		this.rateLimitContext = rateLimitContext;
	}
	
	@Field(group = "rateLimiting", comment = "The action to use for rate limiting, by default the service id will be used as action")
	public String getRateLimitAction() {
		return rateLimitAction;
	}
	public void setRateLimitAction(String rateLimitAction) {
		this.rateLimitAction = rateLimitAction;
	}
	
	@Comment(title = "Whether or not we want to expose a geo position header (if available)")
	@Field(group = "enrichInput")
	public boolean isGeoPosition() {
		return geoPosition;
	}
	public void setGeoPosition(boolean geoPosition) {
		this.geoPosition = geoPosition;
	}
	
	@Advanced
	@Field(comment = "If you enable this, the REST service will keep responding even if the server is turned offline")
	public boolean isIgnoreOffline() {
		return ignoreOffline;
	}
	public void setIgnoreOffline(boolean ignoreOffline) {
		this.ignoreOffline = ignoreOffline;
	}
	
	@Advanced
	@Field(comment = "Enable this if you are using JSON and you want to be able to receive a request that has an array as the root. Note that your defined type must have a single array at the root of the content.")
	public boolean isAllowRootArrays() {
		return allowRootArrays;
	}
	public void setAllowRootArrays(boolean allowRootArrays) {
		this.allowRootArrays = allowRootArrays;
	}
	
	
	
}
