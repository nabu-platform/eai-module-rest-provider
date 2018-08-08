package be.nabu.eai.module.rest.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.http.virtual.VirtualHostArtifact;
import be.nabu.eai.module.http.virtual.api.SourceImpl;
import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.rest.SPIBindingProvider;
import be.nabu.eai.module.rest.WebResponseType;
import be.nabu.eai.module.rest.api.BindingProvider;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationUtils;
import be.nabu.eai.module.web.application.rate.RateLimiter;
import be.nabu.eai.repository.Notification;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.DeviceValidator;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.RoleHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.impl.VariableOperation;
import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.evaluator.types.operations.TypesOperationProvider;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.AuthenticationHeader;
import be.nabu.libs.http.api.server.Session;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPFormatter;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.glue.impl.ResponseMethods;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.CollectionFormat;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.form.FormBinding;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.properties.CollectionFormatProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiableHeader;
import be.nabu.utils.mime.impl.FormatException;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RESTFragmentListener implements EventHandler<HTTPRequest, HTTPResponse> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private PathAnalysis pathAnalysis;
	private String serverPath;
	private DefinedService service;
	private Charset charset;
	private boolean allowEncoding;
	private RESTInterfaceArtifact webArtifact;
	private WebApplication webApplication;
	private ComplexContent configuration;
	private BindingProvider bindingProvider;
	
	private Map<String, TypeOperation> analyzedOperations = new HashMap<String, TypeOperation>();
	private DefinedService cacheService;

	public RESTFragmentListener(WebApplication webApplication, String serverPath, RESTInterfaceArtifact webArtifact, DefinedService service, Charset charset, boolean allowEncoding, DefinedService cacheService) throws IOException {
		this.webApplication = webApplication;
		this.serverPath = serverPath;
		this.webArtifact = webArtifact;
		this.service = service;
		this.charset = charset;
		this.allowEncoding = allowEncoding;
		this.cacheService = cacheService;
		String path = webArtifact.getConfiguration().getPath();
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		this.pathAnalysis = GlueListener.analyzePath(path, TypeBaseUtils.getRegexes(webArtifact.getPath()), !webArtifact.getConfig().isCaseInsensitive());
		DefinedType configurationType = webArtifact.getConfiguration().getConfigurationType();
		if (configurationType != null) {
			String configurationPath = serverPath == null ? "/" : serverPath;
			if (!configurationPath.endsWith("/")) {
				configurationPath += "/";
			}
			configurationPath += path;
			this.configuration = webApplication.getConfigurationFor(configurationPath, (ComplexType) configurationType);
		}
	}
	
	protected TypeOperation getOperation(String query) throws ParseException {
		if (!analyzedOperations.containsKey(query)) {
			synchronized(analyzedOperations) {
				if (!analyzedOperations.containsKey(query))
					analyzedOperations.put(query, (TypeOperation) new PathAnalyzer<ComplexContent>(new TypesOperationProvider()).analyze(QueryParser.getInstance().parse(query)));
			}
		}
		return analyzedOperations.get(query);
	}
	
	protected Object getVariable(ComplexContent pipeline, String query) throws ServiceException {
		VariableOperation.registerRoot();
		try {
			return getOperation(query).evaluate(pipeline);
		}
		catch (EvaluationException e) {
			throw new ServiceException(e);
		}
		catch (ParseException e) {
			throw new ServiceException(e);
		}
		finally {
			VariableOperation.unregisterRoot();
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public HTTPResponse handle(HTTPRequest request) {
		Token token = null;
		try {
			ServiceRuntime.setGlobalContext(new HashMap<String, Object>());
			// stop fast if wrong method
			if (webArtifact.getConfiguration().getMethod() != null && !webArtifact.getConfiguration().getMethod().toString().equalsIgnoreCase(request.getMethod())) {
				return null;
			}
			URI uri = HTTPUtils.getURI(request, false);
			String path = URIUtils.normalize(uri.getPath());
			// not in this web artifact
			if (!path.startsWith(serverPath)) {
				return null;
			}
			path = path.substring(serverPath.length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			Map<String, String> analyzed = pathAnalysis.analyze(path);
			logger.debug("Analyzed: " + analyzed + " in path: " + webArtifact.getConfiguration().getPath());
			// not in this rest path
			if (analyzed == null) {
				return null;
			}
			
			// do a content type check, we do not allow form content types by default
			Header contentTypeHeader = MimeUtils.getHeader("Content-Type", request.getContent().getHeaders());
			String contentType = contentTypeHeader == null ? null : contentTypeHeader.getValue().trim().replaceAll(";.*$", "");
			
			// text/plain is allowed in HTML5 (https://developer.mozilla.org/en-US/docs/Web/HTML/Element/form)
			if (contentType != null && (WebResponseType.FORM_ENCODED.getMimeType().equalsIgnoreCase(contentType) || "multipart/form-data".equalsIgnoreCase(contentType)) || "text/plain".equalsIgnoreCase(contentType)) {
				if (!webArtifact.getConfig().isAllowFormBinding()) {
					throw new HTTPException(400, "Form binding not allowed: " + contentType);
				}
			}
			
			// do a referer check, we only allow cookies to be used if the referer matches the virtual host, because we are dealing with rest services there is no "initial page" scenario to keep track off
			Header refererHeader = MimeUtils.getHeader("Referer", request.getContent().getHeaders());
			URI referer = refererHeader == null ? null : new URI(URIUtils.encodeURI(refererHeader.getValue()));
			
			boolean refererMatch = false;
			if (referer != null) {
				VirtualHostArtifact virtualHost = webApplication.getConfig().getVirtualHost();
				if (referer.getHost() != null) {
					refererMatch = referer.getHost().equals(virtualHost.getConfig().getHost());
					if (!refererMatch) {
						List<String> aliases = virtualHost.getConfig().getAliases();
						if (aliases != null) {
							for (String alias : aliases) {
								refererMatch = referer.getHost().equals(alias);
								if (refererMatch) {
									break;
								}
							}
						}
					}
				}
				if (!refererMatch && webArtifact.getConfig().isAllowCookiesWithExternalReferer()) {
					refererMatch = true;
				}
			}
			else if (webArtifact.getConfig().isAllowCookiesWithoutReferer()) {
				refererMatch = true;
			}
			
			// only use the cookies if we have a referer match
			Map<String, List<String>> cookies = refererMatch ? HTTPUtils.getCookies(request.getContent().getHeaders()) : new HashMap<String, List<String>>();
			String originalSessionId = GlueListener.getSessionId(cookies);
			Session session = originalSessionId == null ? null : webApplication.getSessionProvider().getSession(originalSessionId);
			
			// authentication tokens in the request get precedence over session-based authentication
			AuthenticationHeader authenticationHeader = HTTPUtils.getAuthenticationHeader(request);
			token = authenticationHeader == null ? null : authenticationHeader.getToken();
			
			Device device = null;
			boolean isNewDevice = false;
			String deviceId = null;
			
			// system principals don't follow standard security procedure
			// they can only be set from within the system itself
		// but likely we'll have to check the session for tokens
			if (token == null && session != null) {
				token = (Token) session.get(GlueListener.buildTokenName(webApplication.getRealm()));
			}
			else if (token != null && session != null) {
				session.set(GlueListener.buildTokenName(webApplication.getRealm()), token);
			}
			
			// check validity of token
			TokenValidator tokenValidator = webApplication.getTokenValidator();
			if (tokenValidator != null) {
				if (token != null && !tokenValidator.isValid(token)) {
					if (session != null) {
						session.destroy();
						session = null;
					}
					originalSessionId = null;
					token = null;
				}
			}

			ServiceRuntime.getGlobalContext().put("session", session);
			
			DeviceValidator deviceValidator = webApplication.getDeviceValidator();
			// check validity of device
			device = request.getContent() == null ? null : GlueListener.getDevice(webApplication.getRealm(), request.getContent().getHeaders());
			if (device == null && (deviceValidator != null || (webArtifact.getConfig().getDevice() != null && webArtifact.getConfig().getDevice()))) {
				device = GlueListener.newDevice(webApplication.getRealm(), request.getContent().getHeaders());
				deviceId = device.getDeviceId();
				isNewDevice = true;
			}
			
			if (deviceValidator != null && !deviceValidator.isAllowed(token, device)) {
				throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' is using an unauthorized device '" + device.getDeviceId() + "' for service: " + service.getId());
			}

			ServiceRuntime.getGlobalContext().put("device", device);
			
			// check role
			RoleHandler roleHandler = webApplication.getRoleHandler();
			if (roleHandler != null && webArtifact.getConfiguration().getRoles() != null) {
				boolean hasRole = false;
				for (String role : webArtifact.getConfiguration().getRoles()) {
					if (roleHandler.hasRole(token, role)) {
						hasRole = true;
						break;
					}
				}
				if (!hasRole) {
					throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have one of the allowed roles '" + webArtifact.getConfiguration().getRoles() + "' for service: " + service.getId());
				}
			}
			
			// check rate limiting (if any)
			RateLimiter rateLimiter = webApplication.getRateLimiter();
			if (rateLimiter != null) {
				HTTPResponse response = rateLimiter.handle(webApplication, request, new SourceImpl(PipelineUtils.getPipeline().getSourceContext()), token, device, service.getId(), null);
				if (response != null) {
					return response;
				}
			}

			boolean sanitizeInput = webArtifact.getConfiguration().getSanitizeInput() != null && webArtifact.getConfiguration().getSanitizeInput();
			
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			
			ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
			if (input.getType().get("webApplicationId") != null) {
				input.set("webApplicationId", webApplication.getId());
			}
			if (input.getType().get("configuration") != null) {
				input.set("configuration", configuration);
			}
			if (input.getType().get("query") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("query").getType())) {
					String name = element.getName();
					if (webArtifact.getConfig().getNamingConvention() != null) {
						name = webArtifact.getConfig().getNamingConvention().apply(name);
					}
					try {
						input.set("query/" + element.getName(), sanitize(decollect(queryProperties.get(name), element), sanitizeInput));
					}
					catch (Exception e) {
						logger.error("Could not set query value: " + name + " = " + queryProperties.get(name), e);
						throw new HTTPException(500, e);
					}					
				}
			}
			if (input.getType().get("header") != null && request.getContent() != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("header").getType())) {
					Header[] headers = MimeUtils.getHeaders(RESTUtils.fieldToHeader(element.getName()), request.getContent().getHeaders());
					if (headers != null && headers.length > 0) {
						try {
							if (element.getType().isList(element.getProperties())) {
								List<String> values = new ArrayList<String>();
								for (Header header : headers) {
									values.add(MimeUtils.getFullHeaderValue(header));
								}
								input.set("header/" + element.getName(), sanitize(decollect(values, element), sanitizeInput));
							}
							else {
								input.set("header/" + element.getName(), sanitize(MimeUtils.getFullHeaderValue(headers[0]), sanitizeInput));
							}
						}
						catch (Exception e) {
							logger.error("Could not set header value: " + element.getName() + " = " + Arrays.asList(headers), e);
							throw new HTTPException(500, e);
						}
					}
				}
			}
			if (session != null && input.getType().get("session") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("session").getType())) {
					input.set("session/" + element.getName(), sanitize(session.get(element.getName()), sanitizeInput));
				}
			}
			if (input.getType().get("acceptedLanguages") != null && request.getContent() != null) {
				input.set("acceptedLanguages", MimeUtils.getAcceptedLanguages(request.getContent().getHeaders()));
			}
			
			if (input.getType().get("language") != null) {
				input.set("language", WebApplicationUtils.getLanguage(webApplication, request));
			}

			for (String key : analyzed.keySet()) {
				try {
					input.set("path/" + key, sanitize(analyzed.get(key), sanitizeInput));
				}
				catch (Exception e) {
					logger.error("Could not set path value: " + key + " = " + analyzed.get(key), e);
					throw new HTTPException(500, e);
				}
			}
			if (input.getType().get("cookie") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("cookie").getType())) {
					try {
						input.set("cookie/" + element.getName(), sanitize(cookies.get(element.getName()), sanitizeInput));
					}
					catch (Exception e) {
						logger.error("Could not set cookie value: " + element.getName() + " = " + cookies.get(element.getName()), e);
						throw new HTTPException(500, e);
					}
				}
			}
			
			if (input.getType().get("device") != null) {
				input.set("device", device);
			}
			
			if (input.getType().get("content") != null && request.getContent() instanceof ContentPart) {
				ReadableContainer<ByteBuffer> readable = ((ContentPart) request.getContent()).getReadable();
				// the readable can be null (e.g. empty part)
				if (readable != null) {
					// we want the stream
					if (input.getType().get("content").getType() instanceof SimpleType) {
						input.set("content", IOUtils.toInputStream(readable));
						if (webArtifact.getConfig().getInputAsStream() != null && webArtifact.getConfig().getInputAsStream()) {
							input.set("meta/contentType", MimeUtils.getContentType(request.getContent().getHeaders()));
							input.set("meta/fileName", MimeUtils.getName(request.getContent().getHeaders()));
						}
					}
					else {
						UnmarshallableBinding binding;
						if (contentType == null) {
							throw new HTTPException(400, "Unknown request content type");
						}
						else if (contentType.equalsIgnoreCase("application/xml") || contentType.equalsIgnoreCase("text/xml")) {
							binding = new XMLBinding((ComplexType) input.getType().get("content").getType(), charset);
							if (webArtifact.getConfig().getLenient()) {
								((XMLBinding) binding).setIgnoreUndefined(true);
							}
						}
						else if (contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("application/javascript")) {
							binding = new JSONBinding((ComplexType) input.getType().get("content").getType(), charset);
							if (webArtifact.getConfig().getLenient()) {
								((JSONBinding) binding).setIgnoreUnknownElements(true);
							}
						}
						// we make an exception for form binding
						// we usually do not need it (unless supporting ancient html stuff) but it _can_ pose a CSRF security risk if exposed by default
						else if (contentType.equalsIgnoreCase(WebResponseType.FORM_ENCODED.getMimeType())) {
							if (webArtifact.getConfig().isAllowFormBinding()) {
								binding = new FormBinding((ComplexType) input.getType().get("content").getType(), charset);
							}
							else {
								throw new HTTPException(400, "Form binding not allowed for this rest service");
							}
						}
						else {
							binding = getBindingProvider().getUnmarshallableBinding((ComplexType) input.getType().get("content").getType(), charset, request.getContent().getHeaders());
							if (binding == null) {
								throw new HTTPException(400, "Unsupported request content type: " + contentType);
							}
						}
						try {
							input.set("content", sanitize(binding.unmarshal(IOUtils.toInputStream(readable), new Window[0]), sanitizeInput));
						}
						catch (IOException e) {
							throw new HTTPException(500, e);
						}
						catch (ParseException e) {
							throw new HTTPException(400, "Message can not be parsed using specification: " + input.getType().get("content").getType(),e);
						}
					}
				}
			}
			
			// check permissions
			PermissionHandler permissionHandler = webApplication.getPermissionHandler();
			if (permissionHandler != null) {
				String context = null;
				String action = null;
				if (webArtifact.getConfig().getPermissionContext() != null) {
					if (webArtifact.getConfig().getPermissionContext().startsWith("=")) {
						// we replace any "input/" references as you likely copy pasted it from the interface, it should work the same as the pipeline
						Object result = getVariable(input, webArtifact.getConfig().getPermissionContext().substring(1).replaceAll("\\binput/", ""));
						context = result == null ? null : result.toString();
					}
					else {
						context = webArtifact.getConfig().getPermissionContext();
					}
				}
				if (webArtifact.getConfig().getPermissionAction() != null) {
					if (webArtifact.getConfig().getPermissionAction().startsWith("=")) {
						// we replace any "input/" references as you likely copy pasted it from the interface, it should work the same as the pipeline
						Object result = getVariable(input, webArtifact.getConfig().getPermissionAction().substring(1).replaceAll("\\binput/", ""));
						action = result == null ? null : result.toString();
					}
					else {
						action = webArtifact.getConfig().getPermissionAction();
					}
				}
				if (action != null && !permissionHandler.hasPermission(token, context, action)) {
					throw new HTTPException(token == null ? 401 : 403, "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to '" + request.getMethod().toLowerCase() + "' on '" + path + "' with service: " + service.getId());
				}
			}
			
			// if we have a cache service, let's check if it has changed
			if (this.cacheService != null) {
				boolean isGet = "GET".equalsIgnoreCase(request.getMethod());

				// we differentiate headers for GET requests and other requests
				// for GET we want a 304 back to indicate nothing has changed
				// for other methods we want to validate that the previous version is still valid before we update it through a POST/PUT/...
				Header lastModifiedHeader = isGet 
					? MimeUtils.getHeader("If-Modified-Since", request.getContent().getHeaders()) 
					: MimeUtils.getHeader("If-Unmodified-Since", request.getContent().getHeaders());

				Header etagHeader = isGet 
					? MimeUtils.getHeader("If-None-Match", request.getContent().getHeaders())
					: MimeUtils.getHeader("If-Match", request.getContent().getHeaders());
					
				if (lastModifiedHeader != null || etagHeader != null) {
					ComplexContent cacheInput = Structure.cast(input, cacheService.getServiceInterface().getInputDefinition());
					Date lastModified = lastModifiedHeader == null ? new Date() : HTTPUtils.parseDate(lastModifiedHeader.getValue());
					if (lastModifiedHeader != null) {
						cacheInput.set("cache/lastModified", lastModified);
					}
					if (etagHeader != null) {
						cacheInput.set("cache/etag", etagHeader.getValue());
					}
					ServiceRuntime runtime = new ServiceRuntime(cacheService, webApplication.getRepository().newExecutionContext(token));
					// we set the service context to the web application, rest services can be mounted in multiple applications
					ServiceUtils.setServiceContext(runtime, webApplication.getId());
					ComplexContent cacheOutput = runtime.run(cacheInput);
					Boolean hasChanged = (Boolean) cacheOutput.get("hasChanged");
					// for GET requests: unless we explicitly state that it has changed, we assume it hasn't
					if (isGet && (hasChanged == null || !hasChanged)) {
						List<Header> headers = new ArrayList<Header>();
						// let's see if we explicitly set an expiration time (in seconds)
						Integer maxAge = (Integer) cacheOutput.get("maxAge");
						if (maxAge != null && maxAge >= 0) {
							headers.add(new MimeHeader("Expires", HTTPUtils.formatDate(new Date(lastModified.getTime() + (1000l * maxAge)))));
						}
						if (lastModifiedHeader != null) {
							headers.add(new MimeHeader("Last-Modified", lastModifiedHeader.getValue()));
						}
						if (etagHeader != null) {
							headers.add(new MimeHeader("ETag", etagHeader.getValue()));
						}
						headers.add(new MimeHeader("Content-Length", "0"));
						return new DefaultHTTPResponse(request, 304, HTTPCodes.getMessage(304), new PlainMimeEmptyPart(null,
							headers.toArray(new Header[headers.size()])
						));
					}
					// if it is not a GET request and the resource has changed, we block the request
					else if (!isGet && hasChanged != null && hasChanged) {
						List<Header> headers = new ArrayList<Header>();
						headers.add(new MimeHeader("Content-Length", "0"));
						return new DefaultHTTPResponse(request, 412, HTTPCodes.getMessage(412), new PlainMimeEmptyPart(null,
							headers.toArray(new Header[headers.size()])
						));
					}
				}
			}
			
			if (webArtifact.getConfiguration().getAsynchronous() != null && webArtifact.getConfiguration().getAsynchronous()) {
				webApplication.getRepository().getServiceRunner().run(service, webApplication.getRepository().newExecutionContext(token), input);
				return HTTPUtils.newEmptyResponse(request);
			}
			else {
				ServiceRuntime runtime = new ServiceRuntime(service, webApplication.getRepository().newExecutionContext(token));
				// we set the service context to the web application, rest services can be mounted in multiple applications
				ServiceUtils.setServiceContext(runtime, webApplication.getId());
				ComplexContent output = runtime.run(input);
				List<Header> headers = new ArrayList<Header>();
				if (output != null && output.get("header") != null) {
					ComplexContent header = (ComplexContent) output.get("header");
					for (Element<?> element : header.getType()) {
						Object value = header.get(element.getName());
						if (value != null) {
							if (!(value instanceof String)) {
								value = ConverterFactory.getInstance().getConverter().convert(value, String.class);
								if (value == null) {
									throw new IllegalArgumentException("Can not cast value for header '" + element.getName() + "' to string");
								}
							}
							headers.add(new MimeHeader(RESTUtils.fieldToHeader(element.getName()), (String) value));
						}
					}
				}
				if (output != null && output.get("cache") != null) {
					Date lastModified = (Date) output.get("cache/lastModified");
					if (lastModified != null) {
						headers.add(new MimeHeader("Last-Modified", HTTPUtils.formatDate(lastModified)));
					}
					String etag = (String) output.get("cache/etag");
					if (etag != null) {
						headers.add(new MimeHeader("ETag", etag));
					}
					Boolean mustRevalidate = (Boolean) output.get("cache/mustRevalidate");
					if (mustRevalidate != null && mustRevalidate) {
						headers.add(new MimeHeader("Cache-Control", "no-cache, must-revalidate"));
					}
				}
				if (output != null && output.get("meta") != null) {
					String customContentType = (String) output.get("meta/contentType");
					if (customContentType != null) {
						headers.add(new MimeHeader("Content-Type", customContentType));
					}
					Long customContentLength = (Long) output.get("meta/contentLength");
					if (customContentLength != null) {
						headers.add(new MimeHeader("Content-Length", customContentLength.toString()));
					}
					String fileName = (String) output.get("meta/fileName");
					if (fileName != null) {
						headers.add(new MimeHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\""));
					}
				}
				if (isNewDevice) {
					ModifiableHeader cookieHeader = HTTPUtils.newSetCookieHeader(
						"Device-" + webApplication.getRealm(), 
						deviceId,
						new Date(new Date().getTime() + 1000l*60*60*24*365*100),
						webApplication.getCookiePath(),
						// domain
						null, 
						// secure TODO?
						false,
						// http only
						true
					);
					headers.add(cookieHeader);
				}
				// if there is no content to respond with, just send back an empty response
				if (output == null || output.get("content") == null) {
					return HTTPUtils.newEmptyResponse(request, headers.toArray(new Header[headers.size()]));
				}
				else if (output.get("content") instanceof InputStream) {
					// no size given, set chunked
					if (MimeUtils.getHeader("Content-Length", headers.toArray(new Header[headers.size()])) == null) {
						headers.add(new MimeHeader("Transfer-Encoding", "chunked"));
					}
					// no type given, set default
					if (MimeUtils.getHeader("Content-Type", headers.toArray(new Header[headers.size()])) == null) {
						headers.add(new MimeHeader("Content-Type", "application/octet-stream"));
					}
					PlainMimeContentPart part = new PlainMimeContentPart(null,
						IOUtils.wrap((InputStream) output.get("content")),
						headers.toArray(new Header[headers.size()])
					);
					if (allowEncoding) {
						HTTPUtils.setContentEncoding(part, request.getContent().getHeaders());
					}
					return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), part);
				}
				else {
					Object object = output.get("content");
					output = object instanceof ComplexContent ? (ComplexContent) object : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
					MarshallableBinding binding = request.getContent() == null ? null : getBindingProvider().getMarshallableBinding(output.getType(), charset, request.getContent().getHeaders());
					if (binding != null) {
						contentType = getBindingProvider().getContentType(binding);
					}
					else {
						List<String> acceptedContentTypes = request.getContent() != null
							? MimeUtils.getAcceptedContentTypes(request.getContent().getHeaders())
							: new ArrayList<String>();
						acceptedContentTypes.retainAll(ResponseMethods.allowedTypes);
						WebResponseType preferredResponseType = webArtifact.getConfiguration().getPreferredResponseType();
						if (preferredResponseType == null) {
							preferredResponseType = WebResponseType.JSON;
						}
						contentType = acceptedContentTypes.isEmpty() ? preferredResponseType.getMimeType() : acceptedContentTypes.get(0);
						if (contentType.equalsIgnoreCase(WebResponseType.XML.getMimeType())) {
							binding = new XMLBinding(output.getType(), charset);
						}
						else if (contentType.equalsIgnoreCase(WebResponseType.JSON.getMimeType())) {
							binding = new JSONBinding(output.getType(), charset);
						}
						else if (contentType.equalsIgnoreCase(WebResponseType.FORM_ENCODED.getMimeType())) {
							binding = new FormBinding(output.getType(), charset);
						}
						else {
							throw new HTTPException(500, "Unsupported response content type: " + contentType);
						}
					}
					if (contentType == null) {
						contentType = "application/octet-stream";
					}
					ByteArrayOutputStream content = new ByteArrayOutputStream();
					binding.marshal(content, (ComplexContent) output);
					byte[] byteArray = content.toByteArray();
					headers.add(new MimeHeader("Content-Length", "" + byteArray.length));
					headers.add(new MimeHeader("Content-Type", contentType + "; charset=" + charset.name()));
					PlainMimeContentPart part = new PlainMimeContentPart(null,
						IOUtils.wrap(byteArray, true),
						headers.toArray(new Header[headers.size()])
					);
					if (allowEncoding) {
						HTTPUtils.setContentEncoding(part, request.getContent().getHeaders());
					}
					return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), part);
				}
			}
		}
		catch (FormatException e) {
			report(request, e, token);
			throw new HTTPException(500, "Error while executing: " + service.getId(), e);
		}
		catch (IOException e) {
			report(request, e, token);
			throw new HTTPException(500, "Error while executing: " + service.getId(), e);
		}
		catch (ServiceException e) {
			if (ServiceRuntime.NO_AUTHORIZATION.equals(e.getCode())) {
				throw new HTTPException(403, e);
			}
			else if (ServiceRuntime.NO_AUTHENTICATION.equals(e.getCode())) {
				throw new HTTPException(401, e);
			}
			// this is the code thrown by the flow service for validation errors
			else if ("VM-4".equals(e.getCode())) {
				throw new HTTPException(400, e);
			}
			else {
				report(request, e, token);
				throw new HTTPException(500, "Error while executing: " + service.getId(), e);
			}
		}
		catch (HTTPException e) {
			report(request, e, token);
			throw e;
		}
		catch (Exception e) {
			report(request, e, token);
			throw new HTTPException(500, "Error while executing: " + service.getId(), e);
		}
		finally {
			ServiceRuntime.setGlobalContext(null);
		}
	}
	
	private void report(HTTPRequest request, Exception e, Token token) {
		HTTPFormatter formatter = new HTTPFormatter();
		// do not allow binary, we are stringifying the request
		formatter.getFormatter().setAllowBinary(false);
		// do not allow cookies to be stored, for GDPR reasons (they might contain identifiable information)
		formatter.getFormatter().ignoreHeaders("Cookie");
		String content = null;
		ByteBuffer byteBuffer = IOUtils.newByteBuffer();
		try {
			formatter.formatRequest(request, byteBuffer);
			content = new String(IOUtils.toBytes(byteBuffer), "UTF-8");
		}
		catch (Exception f) {
			logger.warn("Can not generate report", f);
		}
		
		Notification notification = new Notification();
		if (token != null) {
			notification.setAlias(token.getName());
			notification.setRealm(token.getRealm());
		}
		// the application is the context, not the rest service as that might be a generic one
		notification.setServiceContext(webApplication.getId());
		notification.setContext(Arrays.asList(service.getId(), webApplication.getId()));
		notification.setType("nabu.web.rest.provider");
		notification.setCode(e instanceof HTTPException ? ((HTTPException) e).getCode() : 0);
		notification.setMessage("REST Request failed" + (e == null ? "" : ": " + e.getMessage()));
		notification.setDescription(content + "\n\n" + Notification.format(e));
		notification.setSeverity(Severity.ERROR);
		
		webApplication.getRepository().getEventDispatcher().fire(notification, this);
	}

	private List<String> decollect(List<String> list, Element<?> element) {
		Value<CollectionFormat> property = element.getProperty(CollectionFormatProperty.getInstance());
		if (property == null || property.getValue() == CollectionFormat.MULTI) {
			return list;
		}
		List<String> result = new ArrayList<String>();
		for (String part : list) {
			result.addAll(Arrays.asList(part.split("\\Q" + property.getValue().getCharacter() + "\\E")));
		}
		return result;
	}

	private static Object sanitize(Object value, boolean sanitize) {
		return sanitize ? GlueListener.sanitize(value) : value;
	}

	public BindingProvider getBindingProvider() {
		if (bindingProvider == null) {
			bindingProvider = SPIBindingProvider.getInstance();
		}
		return bindingProvider;
	}

	public void setBindingProvider(BindingProvider bindingProvider) {
		this.bindingProvider = bindingProvider;
	}
	
}
