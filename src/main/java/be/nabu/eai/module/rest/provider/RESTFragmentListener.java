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
import be.nabu.eai.module.web.application.TemporaryAuthenticationImpl;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationUtils;
import be.nabu.eai.module.web.application.api.TemporaryAuthenticator;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.Notification;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.DeviceValidator;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.PotentialPermissionHandler;
import be.nabu.libs.authentication.api.RoleHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.TokenValidator;
import be.nabu.libs.authentication.api.TokenWithSecret;
import be.nabu.libs.cache.api.Cache;
import be.nabu.libs.cache.api.CacheEntry;
import be.nabu.libs.cache.api.CacheWithHash;
import be.nabu.libs.cache.api.ExplorableCache;
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
import be.nabu.libs.http.api.HTTPInterceptor;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.AuthenticationHeader;
import be.nabu.libs.http.api.server.Session;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPFormatter;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.core.ServerHeader;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.http.glue.impl.ResponseMethods;
import be.nabu.libs.nio.PipelineUtils;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
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
import be.nabu.libs.types.properties.AliasProperty;
import be.nabu.libs.types.properties.CollectionFormatProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
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

/**
 * Security checks are ALWAYS run in he "default" service context (which is the application itself)
 * However, the user CAN request a different service context for the final service run. The ability to switch the context is itself behind a permission check in the default service context.
 */
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
	private boolean persistAuthenticationChange = false;
	
	private Map<String, TypeOperation> analyzedOperations = new HashMap<String, TypeOperation>();
	private DefinedService cacheService;

	public RESTFragmentListener(WebApplication webApplication, String serverPath, RESTInterfaceArtifact webArtifact, DefinedService service, Charset charset, boolean allowEncoding, DefinedService cacheService) throws IOException {
		this.webApplication = webApplication;
		this.serverPath = serverPath;
		this.webArtifact = webArtifact;
		this.service = service;
		this.charset = charset;
		// we now handle this at a higher level
		this.allowEncoding = allowEncoding && false;
		this.cacheService = cacheService;
		String path = webArtifact.getPath();
		// make up a more-or-less unique path
		if (path == null || path.trim().isEmpty()) {
			path = service.getId();
		}
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		this.pathAnalysis = GlueListener.analyzePath(path, TypeBaseUtils.getRegexes(webArtifact.getPathParameters()), !webArtifact.getConfig().isCaseInsensitive());
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
		Device device = null;
		boolean isForThisService = false;
		try {
			ServiceRuntime.setGlobalContext(new HashMap<String, Object>());
			ServiceRuntime.getGlobalContext().put("service.context", webApplication.getId());
			ServiceRuntime.getGlobalContext().put("webApplicationId", webApplication.getId());
			ServiceRuntime.getGlobalContext().put("service.source", "rest");
			String method = webArtifact.getMethod();
			// stop fast if wrong method
			if (method != null && !method.equalsIgnoreCase(request.getMethod())) {
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
			
			isForThisService = true;
			
			// if we have chosen this rest service, check if the server is offline
			if (!webArtifact.getConfig().isIgnoreOffline()) {
				WebApplicationUtils.checkOffline(webApplication, request);
			}
			
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			
			// if we are using overrides, we also want to allow service context via header
			boolean usingHeaderOverrides = false;
			// if we allow (some) headers as query parameter, override the request
			// we currently allow you to fixate the "accept" to determine the return type, the language and the content disposition (to force a download)
			if (webArtifact.getConfig().isAllowHeaderAsQueryParameter()) {
				for (String key : queryProperties.keySet()) {
					if (key.startsWith("header:") && !queryProperties.get(key).isEmpty()) {
						String headerName = key.substring("header:".length());
						for (String allowed : Arrays.asList("Accept", "Accept-Language", "Accept-Content-Disposition")) {
							if (headerName.equalsIgnoreCase(allowed)) {
								usingHeaderOverrides = true;
								request.getContent().removeHeader(allowed);
								request.getContent().setHeader(MimeHeader.parseHeader(allowed + ":" + queryProperties.get(key).get(0)));
								break;
							}
						}
					}
				}
			}
			
			// do a content type check, we do not allow form content types by default
			Header contentTypeHeader = MimeUtils.getHeader("Content-Type", request.getContent().getHeaders());
			String contentType = contentTypeHeader == null ? null : contentTypeHeader.getValue().trim().replaceAll(";.*$", "");
			
			// text/plain is allowed in HTML5 (https://developer.mozilla.org/en-US/docs/Web/HTML/Element/form)
			// if we have an input as stream however, it is still allowed because we don't really care about the content type
			if (contentType != null && (webArtifact.getConfig().getInputAsStream() == null || webArtifact.getConfig().getInputAsStream() == false) && (WebResponseType.FORM_ENCODED.getMimeType().equalsIgnoreCase(contentType) || "multipart/form-data".equalsIgnoreCase(contentType)) || "text/plain".equalsIgnoreCase(contentType)) {
				if (!webArtifact.getConfig().isAllowFormBinding()) {
					throw new HTTPException(415, "Form binding not allowed", "Form binding not allowed: " + contentType, token);
				}
			}
			
			// do a referer check, we only allow cookies to be used if the referer matches the virtual host, because we are dealing with rest services there is no "initial page" scenario to keep track off
			Header refererHeader = MimeUtils.getHeader("Referer", request.getContent().getHeaders());
			URI referer = refererHeader == null ? null : new URI(URIUtils.encodeURI(refererHeader.getValue()));
			
			boolean refererMatch = false;
			if (referer != null) {
				VirtualHostArtifact virtualHost = webApplication.getConfig().getVirtualHost();
				// we can only check if we filled in the host
				if (virtualHost.getConfig().getHost() != null) {
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
				// evidently we don't care about the referer matching capabilities...
				// in one particular case we deployed an application without a host alias, it took 2 hours to figure out why it wasn't working...
				else {
					refererMatch = true;
				}
				if (!refererMatch && webArtifact.getConfig().isAllowCookiesWithExternalReferer()) {
					refererMatch = true;
				}
			}
			else if (webArtifact.getConfig().isAllowCookiesWithoutReferer()) {
				refererMatch = true;
			}
			
			// only use the cookies if we have a referer match (or are in development modus, it makes debugging slightly easier)
			Map<String, List<String>> cookies = refererMatch || EAIResourceRepository.isDevelopment() ? HTTPUtils.getCookies(request.getContent().getHeaders()) : new HashMap<String, List<String>>();
			String originalSessionId = GlueListener.getSessionId(cookies);
			Session session = originalSessionId == null || webApplication.getSessionProvider() == null ? null : webApplication.getSessionProvider().getSession(originalSessionId);
			
			// authentication tokens in the request get precedence over session-based authentication
			AuthenticationHeader authenticationHeader = HTTPUtils.getAuthenticationHeader(request);
			token = authenticationHeader == null ? null : authenticationHeader.getToken();
			
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
				throw new HTTPException(token == null ? 401 : 403, "User is using an unauthorized device", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' is using an unauthorized device '" + device.getDeviceId() + "' for service: " + service.getId(), token);
			}

			ServiceRuntime.getGlobalContext().put("device", device);
			
			TemporaryAuthenticator temporaryAuthenticator = webApplication.getTemporaryAuthenticator();
			
			// check role
			RoleHandler roleHandler = webApplication.getRoleHandler();
			// if we have a temporary alias, we are pulling it from the input and we can only perform any checks after we have parsed the input
			// in every other case (which is the vast majority of cases) it is interesting to do the role check as early as possible to save resources on parsing the input
			if (roleHandler != null && webArtifact.getConfiguration().getRoles() != null && (webArtifact.getConfig().getTemporaryAlias() == null || temporaryAuthenticator == null)) {
				boolean hasRole = false;
				for (String role : webArtifact.getConfiguration().getRoles()) {
					if (roleHandler.hasRole(token, role)) {
						hasRole = true;
						break;
					}
				}
				if (!hasRole) {
					throw new HTTPException(token == null ? 401 : 403, "User does not have one of the allowed roles", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have one of the allowed roles '" + webArtifact.getConfiguration().getRoles() + "' for service: " + service.getId(), token);
				}
			}
			
			SourceImpl source = PipelineUtils.getPipeline() == null ? new SourceImpl() : new SourceImpl(PipelineUtils.getPipeline().getSourceContext());
			// if we are being proxied, get the "actual" data
			if (request.getContent() != null && webApplication.getConfig().getVirtualHost().isProxied()) {
				source.setRemoteHost(HTTPUtils.getRemoteHost(true, request.getContent().getHeaders()));
				source.setRemoteIp(HTTPUtils.getRemoteAddress(true, request.getContent().getHeaders()));
				Header header = MimeUtils.getHeader(ServerHeader.REMOTE_PORT.getName(), request.getContent().getHeaders());
				if (header != null && header.getValue() != null) {
					source.setRemotePort(Integer.parseInt(header.getValue()));
				}
				header = MimeUtils.getHeader(ServerHeader.LOCAL_PORT.getName(), request.getContent().getHeaders());
				if (header != null && header.getValue() != null) {
					source.setLocalPort(Integer.parseInt(header.getValue()));
				}
			}
			
			boolean sanitizeInput = webArtifact.getConfiguration().getSanitizeInput() != null && webArtifact.getConfiguration().getSanitizeInput();
			
			ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
			if (input.getType().get("webApplicationId") != null) {
				input.set("webApplicationId", webApplication.getId());
			}
			if (input.getType().get("domain") != null) {
				input.set("domain", uri.getHost());
			}
			if (input.getType().get("origin") != null) {
				Header originHeader = MimeUtils.getHeader("Origin", request.getContent().getHeaders());
				if (originHeader != null) {
					input.set("origin", MimeUtils.getFullHeaderValue(originHeader));
				}
			}
			if (input.getType().get("request") != null) {
				input.set("request", request);
			}
			if (input.getType().get("source") != null) {
				input.set("source", source);
			}
			if (input.getType().get("configuration") != null) {
				input.set("configuration", configuration);
			}
			if (input.getType().get("query") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("query").getType())) {
					String name = element.getName();
					// support aliasing for otherwise unsupported naming conventions
					String alias = ValueUtils.getValue(AliasProperty.getInstance(), element.getProperties());
					if (alias != null) {
						name = alias;
					}
					if (webArtifact.getConfig().getNamingConvention() != null) {
						name = webArtifact.getConfig().getNamingConvention().apply(name);
					}
					try {
						input.set("query/" + element.getName(), sanitize(decollect(queryProperties.get(name), element), sanitizeInput));
					}
					catch (Exception e) {
						throw new HTTPException(500, "Could not set query parameter", "Could not set query parameter: " + name + " = " + queryProperties.get(name) + " for service " + service.getId(), e, token);
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
							throw new HTTPException(500, "Could not set header parameter", "Could not set header parameter: " + element.getName() + " = " + Arrays.asList(headers), e, token);
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
					throw new HTTPException(500, "Could not set path parameters", "Could not set path parameter: " + key + " = " + analyzed.get(key), e, token);
				}
			}
			if (input.getType().get("cookie") != null) {
				for (Element<?> element : TypeUtils.getAllChildren((ComplexType) input.getType().get("cookie").getType())) {
					try {
						List<String> value = cookies.get(element.getName());
						// in some cases you can get multiple cookies with the same name on different paths.
						// however, it is hard to distinguish between them because said path info is not supplied by the browser
						// if you request a list of cookies, it doesn't matter, but if you specifically request one cookie, we want to take the first one
						// according to RFC 6265 user agents should sort the cookies according to most specific path first:
						// The user agent SHOULD sort the cookie-list in the following order:
						// Cookies with longer paths are listed before cookies with shorter paths.
						// Among cookies that have equal-length path fields, cookies with earlier creation-times are listed before cookies with later creation-times.
						// NOTE: Not all user agents sort the cookie-list in this order, but this order reflects common practice when this document was written, and, historically, there have been servers that (erroneously) depended on this order.
						if (!element.getType().isList(element.getProperties()) && value != null && value.size() >= 2) {
							value = value.subList(0, 1);
						}
						input.set("cookie/" + element.getName(), sanitize(value, sanitizeInput));
					}
					catch (Exception e) {
						throw new HTTPException(500, "Could not set cookie value", "Could not set cookie value: " + element.getName() + " = " + cookies.get(element.getName()), e, token);
					}
				}
			}
			
			if (input.getType().get("device") != null) {
				input.set("device", device);
			}
			
			if (input.getType().get("token") != null) {
				input.set("token", token);
			}
			
			if (input.getType().get("geoPosition") != null) {
				Header geoPosition = MimeUtils.getHeader("Geo-Position", request.getContent().getHeaders());
				if (geoPosition == null) {
					geoPosition = MimeUtils.getHeader("X-Geo-Position", request.getContent().getHeaders());
				}
				if (geoPosition != null) {
					String headerValue = MimeUtils.getFullHeaderValue(geoPosition);
					try {
						String[] split = headerValue.split("[\\s]*;[\\s]*");
						if (split.length >= 2) {
							Double latitude = Double.parseDouble(split[0]);
							Double longitude = Double.parseDouble(split[1].split("[\\s]+")[0]);
							input.set("geoPosition/latitude", latitude);
							input.set("geoPosition/longitude", longitude);
						}
					}
					catch (Exception e) {
						logger.warn("Invalid geo-position header: " + headerValue);
					}
				}
			}
			
			if (input.getType().get("content") != null && request.getContent() instanceof ContentPart) {
				ReadableContainer<ByteBuffer> readable = ((ContentPart) request.getContent()).getReadable();
				// the readable can be null (e.g. empty part)
				if (readable != null) {
					// we want the stream
					if (input.getType().get("content").getType() instanceof SimpleType) {
						HTTPResponse scanResponse = WebApplicationUtils.scanForVirus(service, webApplication, device, token, request);
						if (scanResponse != null) {
							return scanResponse;
						}
						
						input.set("content", IOUtils.toInputStream(readable));
						if (webArtifact.getConfig().getInputAsStream() != null && webArtifact.getConfig().getInputAsStream()) {
							input.set("meta/contentType", MimeUtils.getContentType(request.getContent().getHeaders()));
							input.set("meta/fileName", MimeUtils.getName(request.getContent().getHeaders()));
						}
					}
					else {
						UnmarshallableBinding binding;
						if (contentType == null) {
							throw new HTTPException(415, "Unsupported request content type", "Unsupported request content type: " + contentType, token);
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
							if (webArtifact.getConfig().isAllowRootArrays()) {
								((JSONBinding) binding).setIgnoreRootIfArrayWrapper(true);
							}
						}
						// we make an exception for form binding
						// we usually do not need it (unless supporting ancient html stuff) but it _can_ pose a CSRF security risk if exposed by default
						else if (contentType.equalsIgnoreCase(WebResponseType.FORM_ENCODED.getMimeType())) {
							if (webArtifact.getConfig().isAllowFormBinding()) {
								binding = new FormBinding((ComplexType) input.getType().get("content").getType(), charset);
							}
							else {
								throw new HTTPException(415, "Form binding not allowed for rest service", "Form binding not allowed for rest service: " + webArtifact.getId(), token);
							}
						}
						else {
							binding = getBindingProvider().getUnmarshallableBinding((ComplexType) input.getType().get("content").getType(), charset, request.getContent().getHeaders());
							if (binding == null) {
								throw new HTTPException(415, "Unsupported request content type", "Unsupported request content type: " + contentType, token);
							}
						}
						try {
							ComplexContent unmarshalled = binding.unmarshal(IOUtils.toInputStream(readable), new Window[0]);
							HTTPResponse scanResponse = WebApplicationUtils.scanForVirus(service, webApplication, device, token, request, unmarshalled);
							if (scanResponse != null) {
								return scanResponse;
							}
							input.set("content", sanitize(unmarshalled, sanitizeInput));
						}
						catch (IOException e) {
							throw new HTTPException(500, "Unexpected I/O exception", e, token);
						}
						catch (ParseException e) {
							throw new HTTPException(400, "Message can not be parsed", "Message can not be parsed using specification: " + input.getType().get("content").getType(), e, token);
						}
					}
				}
			}

			// this allows for temporarily valid tokens like one-time use or limited in time access to e.g. a file download
			if (webArtifact.getConfig().getTemporaryAlias() != null && temporaryAuthenticator != null) {
				String alias = null, secret = null, correlationId = null;
				if (webArtifact.getConfig().getTemporaryAlias().startsWith("=")) {
					Object result = getVariable(input, webArtifact.getConfig().getTemporaryAlias().substring(1).replaceAll("\\binput/", ""));
					alias = result == null ? null : ConverterFactory.getInstance().getConverter().convert(result, String.class);
				}
				else {
					alias = webArtifact.getConfig().getTemporaryAlias();
				}
				// it is possible that we don't send an alias on purpose
				if (alias != null) {
					// the secret "can" be null, in case the alias is unique (e.g. uuid) but preferably not...
					if (webArtifact.getConfig().getTemporarySecret() != null) {
						if (webArtifact.getConfig().getTemporarySecret().startsWith("=")) {
							Object result = getVariable(input, webArtifact.getConfig().getTemporarySecret().substring(1).replaceAll("\\binput/", ""));
							secret = result == null ? null : ConverterFactory.getInstance().getConverter().convert(result, String.class);
						}
						else {
							secret = webArtifact.getConfig().getTemporarySecret();
						}
					}
					if (webArtifact.getConfig().getTemporaryCorrelationId() != null) {
						if (webArtifact.getConfig().getTemporaryCorrelationId().startsWith("=")) {
							Object result = getVariable(input, webArtifact.getConfig().getTemporaryCorrelationId().substring(1).replaceAll("\\binput/", ""));
							correlationId = result == null ? null : ConverterFactory.getInstance().getConverter().convert(result, String.class);
						}
						else {
							correlationId = webArtifact.getConfig().getTemporaryCorrelationId();
						}
					}
					// this trumps any other token
					// first check for a specific token for this service
					// note that we prefix it with execution because the user can request it for "any" service we don't want him to be able to request for instance "authentication"
					token = temporaryAuthenticator.authenticate(webApplication.getRealm(), new TemporaryAuthenticationImpl(alias, secret), device, TemporaryAuthenticator.EXECUTION + ":" + service.getId(), correlationId);
					if (token == null) {
						// otherwise, check if there is a broader "execution" token
						token = temporaryAuthenticator.authenticate(webApplication.getRealm(), new TemporaryAuthenticationImpl(alias, secret), device, TemporaryAuthenticator.EXECUTION, correlationId);
					}
					// update the token in the input
					if (input.getType().get("token") != null) {
						input.set("token", token);
					}
				}
				if (roleHandler != null && webArtifact.getConfiguration().getRoles() != null) {
					boolean hasRole = false;
					for (String role : webArtifact.getConfiguration().getRoles()) {
						if (roleHandler.hasRole(token, role)) {
							hasRole = true;
							break;
						}
					}
					if (!hasRole) {
						throw new HTTPException(token == null ? 401 : 403, "User does not have one of the allowed roles", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have one of the allowed roles '" + webArtifact.getConfiguration().getRoles() + "' for service: " + service.getId(), token);
					}
				}
			}
			
			boolean outputAsStream = webArtifact.getConfig().getOutputAsStream() != null && webArtifact.getConfig().getOutputAsStream();
			
			// the actual context switching permission HAS to be executed against the original context
			// note that IF you have an output as stream, we allow setting the service context as a query parameter
			// this should not really open a security hole because the security of service context switching is tightly regulated, but we don't want to advertise this capability in general
			String serviceContext = WebApplicationUtils.getServiceContext(token, webApplication, request, outputAsStream || usingHeaderOverrides ? "$serviceContext" : null);
			
			// after that, authorization is handled by the target connection
			ServiceRuntime.getGlobalContext().put("service.context", serviceContext);
			
			// check permissions
			PermissionHandler permissionHandler = webApplication.getPermissionHandler();
			PotentialPermissionHandler potentialPermissionHandler = webApplication.getPotentialPermissionHandler();
			String action = null;
			String context = null;
			if (permissionHandler != null) {
				boolean hasDefinedContext = false;
				if (webArtifact.getConfig().getPermissionContext() != null) {
					if (webArtifact.getConfig().getPermissionContext().startsWith("=")) {
						// we replace any "input/" references as you likely copy pasted it from the interface, it should work the same as the pipeline
						Object result = getVariable(input, webArtifact.getConfig().getPermissionContext().substring(1).replaceAll("\\binput/", ""));
						context = result == null ? null : result.toString();
					}
					else {
						context = webArtifact.getConfig().getPermissionContext();
					}
					hasDefinedContext = true;
				}
				else if (webArtifact.getConfig().isUseServiceContextAsPermissionContext()) {
					// we use the potentially final service context that will be used to run the service, this may have been altered from the standard one used to execute these services
					context = "context:" + serviceContext;
					hasDefinedContext = true;
				}
				else if (webArtifact.getConfig().isUseWebApplicationAsPermissionContext()) {
					context = "context:" + webApplication.getId();
					hasDefinedContext = true;
				}
				else if (webArtifact.getConfig().isUseProjectAsPermissionContext()) {
//					String projectId = project == null ? webArtifact.getId().replaceAll("^([^.]+)\\..*$", "$1") : project.getId();
					context = "context:" + EAIRepositoryUtils.getProject(webArtifact.getRepository().getEntry(service.getId())).getId();
					hasDefinedContext = true;
				}
				else if (webArtifact.getConfig().isUseGlobalPermissionContext()) {
					context = "context:$global";
					hasDefinedContext = true;
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
					boolean allowed = false;
					// if we don't have permission to run it as is, but we have explicitly (at design time) decided not to fill in a context
					// we check the potential permissions as well
					if (!hasDefinedContext && potentialPermissionHandler != null) {
						allowed = potentialPermissionHandler.hasPotentialPermission(token, action);
					}
					if (!allowed) {
						throw new HTTPException(token == null ? 401 : 403, "User does not have permission to execute the rest service", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to '" + request.getMethod().toLowerCase() + "' on '" + path + "' with service: " + service.getId(), token);
					}
				}
			}
			
			// switch back to the web application context for rate limiting!
			ServiceRuntime.getGlobalContext().put("service.context", webApplication.getId());
			
			// let's not bother with rate limiting if it isn't filled in
			if (webApplication.getRateLimiter() != null) {
				String rateLimitAction = webArtifact.getConfig().getRateLimitAction();
				if (rateLimitAction == null) {
					rateLimitAction = webArtifact.getConfig().getPermissionAction();
				}
				if (rateLimitAction == null) {
					rateLimitAction = service.getId();
				}
				String rateLimitContext = webArtifact.getConfig().getRateLimitContext();
				// if you explicitly configured an action, we don't fall back to permissions
				if (rateLimitContext == null && webArtifact.getConfig().getRateLimitAction() == null) {
					rateLimitContext = webArtifact.getConfig().getPermissionContext();
				}
				if (rateLimitContext != null && rateLimitContext.startsWith("=")) {
					Object result = getVariable(input, rateLimitContext.substring(1).replaceAll("\\binput/", ""));
					rateLimitContext = result == null ? null : result.toString();
				}
				HTTPResponse response = WebApplicationUtils.checkRateLimits(webApplication, source, token, device, rateLimitAction, rateLimitContext, request);
				if (response != null) {
					return response;
				}
			}
			
			ExecutionContext newExecutionContext = webApplication.getRepository().newExecutionContext(token);
			// play with the features
			WebApplicationUtils.featureRich(webApplication, request, newExecutionContext);
			
			// if we have a cache service, let's check if it has changed
			// if we have toggled the use of service cache, we don't need an explicit service (though it is still possible)
			if (this.cacheService != null || webArtifact.getConfig().isUseServerCache()) {
				
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
					// having a cache service trumps the use server setting
					if (cacheService != null) {
						ComplexContent cacheInput = Structure.cast(input, cacheService.getServiceInterface().getInputDefinition());
						Date lastModified = lastModifiedHeader == null ? new Date() : HTTPUtils.parseDate(lastModifiedHeader.getValue());
						if (lastModifiedHeader != null) {
							cacheInput.set("clientCache/lastModified", lastModified);
						}
						if (etagHeader != null) {
							cacheInput.set("clientCache/etag", etagHeader.getValue());
						}
						
						// check if we have a cache for the service
						Cache serviceCache = newExecutionContext.getServiceContext().getCacheProvider().get(((DefinedService) service).getId());
						if (serviceCache instanceof ExplorableCache) {
							CacheEntry entry = ((ExplorableCache) serviceCache).getEntry(input);
							if (entry != null) {
								Date serverModified = entry.getLastModified();
								// the http headers do not allow for ms precision, so strip the ms from the date to get a comparable date
								serverModified = new Date(serverModified.getTime() - (serverModified.getTime() % 1000));
								cacheInput.set("serverCache/lastModified", serverModified);
							}
						}
						if (serviceCache instanceof CacheWithHash) {
							cacheInput.set("serverCache/hash", ((CacheWithHash) serviceCache).hash(input));
						}
						
						ServiceRuntime cacheRuntime = new ServiceRuntime(cacheService, webApplication.getRepository().newExecutionContext(token));
						
						Header correlationHeader = MimeUtils.getHeader("X-Correlation-Id", request.getContent().getHeaders());
						if (correlationHeader != null) {
							cacheRuntime.setCorrelationId(correlationHeader.getValue());
						}
						
						// we set the service context to the web application, rest services can be mounted in multiple applications
//						ServiceUtils.setServiceContext(cacheRuntime, webApplication.getId());
						// get the smarter service context
						ServiceUtils.setServiceContext(cacheRuntime, serviceContext);
						
						cacheRuntime.getContext().put("webApplicationId", webApplication.getId());
						ComplexContent cacheOutput = cacheRuntime.run(cacheInput);
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
					// automatic server caching should only work on a GET service?
					else if (isGet) {
						Date lastModified = lastModifiedHeader == null ? new Date() : HTTPUtils.parseDate(lastModifiedHeader.getValue());
						Cache serviceCache = newExecutionContext.getServiceContext().getCacheProvider().get(((DefinedService) service).getId());
						// there has to be _some_ cache
						Boolean hasChanged = null;
						if (lastModified != null) {
							CacheEntry entry = ((ExplorableCache) serviceCache).getEntry(input);
							if (entry != null) {
								Date serverModified = entry.getLastModified();
								serverModified = new Date(serverModified.getTime() - (serverModified.getTime() % 1000));
								hasChanged = lastModified.before(serverModified);
							}
						}
						String etag = etagHeader == null ? null : etagHeader.getValue();
						if (etag != null && (hasChanged == null || !hasChanged)) {
							if (serviceCache instanceof CacheWithHash) {
								hasChanged = !etag.equals(((CacheWithHash) serviceCache).hash(input));
							}
						}
						// if it hasn't changed, send back a response to that effect
						if (hasChanged != null && !hasChanged) {
							List<Header> headers = new ArrayList<Header>();
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
					}
					else {
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
				ServiceRuntime runtime = new ServiceRuntime(service, newExecutionContext);
				runtime.setSlaProvider(webApplication);
				
				// we set the service context to the web application, rest services can be mounted in multiple applications
//				ServiceUtils.setServiceContext(runtime, webApplication.getId());
				
				// get the smarter service context
				ServiceUtils.setServiceContext(runtime, serviceContext);
				
				runtime.getContext().put("webApplicationId", webApplication.getId());
				
				HTTPInterceptor interceptor = WebApplicationUtils.getInterceptor(webApplication, runtime);
				if (interceptor != null) {
					request = (HTTPRequest) interceptor.intercept(request);
				}
				
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
				Date lastModified = output == null ? null : (Date) output.get("cache/lastModified");
				String etag = output == null ? null : (String) output.get("cache/etag");

				// if we want to use the server cache settings, try to get them for values you did not explicitly fill in
				if (webArtifact.getConfig().isUseServerCache()) {
					// check if we have a cache for the service
					Cache serviceCache = newExecutionContext.getServiceContext().getCacheProvider().get(((DefinedService) service).getId());
					if (lastModified == null) {
						if (serviceCache instanceof ExplorableCache) {
							CacheEntry entry = ((ExplorableCache) serviceCache).getEntry(input);
							if (entry != null) {
								lastModified = entry.getLastModified();
								// see above comment for ms precision in http headers
								lastModified = new Date(lastModified.getTime() - (lastModified.getTime() % 1000));
							}
						}
					}
					if (etag == null) {
						if (serviceCache instanceof CacheWithHash) {
							etag = ((CacheWithHash) serviceCache).hash(input);
						}
					}
				}
				if (output != null && (lastModified != null || etag != null)) {
					if (lastModified != null) {
						headers.add(new MimeHeader("Last-Modified", HTTPUtils.formatDate(lastModified)));
					}
					if (etag != null) {
						headers.add(new MimeHeader("ETag", etag));
					}
					// if you don't have explicit control of caching but are using server cache, force the client to revalidate
					// if you want more control over the caching, add the caching component
					Boolean mustRevalidate = !webArtifact.getConfig().isCache() && webArtifact.getConfig().isUseServerCache() ? Boolean.valueOf(true) : (Boolean) output.get("cache/mustRevalidate");
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
				else {
					Map<String, String> values = MimeUtils.getHeaderAsValues("Accept-Content-Disposition", request.getContent().getHeaders());
					// we are asking for an attachment download
					if (values.get("value") != null && values.get("value").equalsIgnoreCase("attachment")) {
						String fileName = values.get("filename");
						if (fileName != null) {
							fileName = fileName.replaceAll("[^\\w.-]+", "");
						}
						else {
							fileName = "unnamed";
						}
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
						webApplication.isSecure(),
						// http only
						true,
						// use the same site cookie policy
						webApplication.getConfig().getDefaultCookieSitePolicy()
					);
					headers.add(cookieHeader);
				}
				if (EAIResourceRepository.isDevelopment()) {
					if (action != null) {
						headers.add(new MimeHeader("X-Checked-Permission-Action", action));
					}
					if (context != null) {
						headers.add(new MimeHeader("X-Checked-Permission-Context", context));
					}
					headers.add(new MimeHeader("X-Service-Id", service.getId()));
					headers.add(new MimeHeader("X-Service-Context", serviceContext));
				}
				Integer responseCode = output == null ? null : (Integer) output.get("responseCode");
				// if there is no content to respond with, just send back an empty response
				if (output == null || output.get("content") == null) {
					if (responseCode == null) {
						// if there is structurally no output, return a 204
						if (webArtifact.getConfig().getOutput() == null && (webArtifact.getConfig().getOutputAsStream() == null || !webArtifact.getConfig().getOutputAsStream())) {
							responseCode = 204;
						}
						// if there is by happenstance no output, return a 200
						else {
							responseCode = 200;
						}
					}
					List<Header> allHeaders = new ArrayList<Header>();
					allHeaders.addAll(headers);
					allHeaders.add(new MimeHeader("Content-Length", "0"));
					HTTPResponse newEmptyResponse = new DefaultHTTPResponse(request, responseCode, HTTPCodes.getMessage(responseCode), new PlainMimeEmptyPart(null,
						allHeaders.toArray(new Header[0])
					));
					if (interceptor != null) {
						newEmptyResponse = (HTTPResponse) interceptor.intercept(newEmptyResponse);
					}
					return newEmptyResponse;
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
					if (responseCode == null) {
						responseCode = 200;
					}
					// we can _not_ reopen this one!
					HTTPResponse streamedResponse = new DefaultHTTPResponse(request, responseCode, HTTPCodes.getMessage(responseCode), part);
					if (interceptor != null) {
						streamedResponse = (HTTPResponse) interceptor.intercept(streamedResponse);
					}
					return streamedResponse;
				}
				else {
					Object object = output.get("content");
					Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), webArtifact.getOutputDefinition().get("content").getProperties());
					boolean isRootList = maxOccurs != null && (maxOccurs >= 2 || maxOccurs == 0);
					// if the output is an array 
					if (isRootList) {
						Structure arrayWrapper = new Structure();
						arrayWrapper.setName("listWrapper");
						arrayWrapper.add(TypeBaseUtils.clone(webArtifact.getOutputDefinition().get("content"), arrayWrapper));
						output = arrayWrapper.newInstance();
						output.set("content", object);
					}
					else {
						output = object instanceof ComplexContent ? (ComplexContent) object : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
					}
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
							// XML can't handle multiple roots, so we leave the wrapper in place in case we have a root array
							binding = new XMLBinding(output.getType(), charset);
						}
						else if (contentType.equalsIgnoreCase(WebResponseType.JSON.getMimeType())) {
							binding = new JSONBinding(output.getType(), charset);
							if (webArtifact.getConfig().isAllowRaw()) {
								((JSONBinding) binding).setAllowRaw(true);
							}
							// JSON can handle root arrays, but we only want it explicitly in this scenario
							((JSONBinding) binding).setIgnoreRootIfArrayWrapper(isRootList);
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
					// we fed it bytes, it can be reopened
					part.setReopenable(true);
					if (allowEncoding) {
						HTTPUtils.setContentEncoding(part, request.getContent().getHeaders());
					}
					if (responseCode == null) {
						responseCode = 200;
					}
					HTTPResponse response = new DefaultHTTPResponse(request, responseCode, HTTPCodes.getMessage(responseCode), part);
					
					// if we upgraded the security with a persistent token, persist it
					Token currentToken = newExecutionContext.getSecurityContext().getToken();
					// someone upgraded the token
					if (persistAuthenticationChange && currentToken != null && currentToken.getRealm() != null && currentToken.getRealm().equals(webApplication.getRealm()) && !currentToken.equals(token)) {
						boolean sslOnly = webApplication.getConfig().getVirtualHost().isSecure();
						if (currentToken instanceof TokenWithSecret) {
							String secret = ((TokenWithSecret) currentToken).getSecret();
							// if we have a secret to remember this token, use it
							if (secret != null) {
								// add new set-cookie header to remember the secret
								response.getContent().setHeader(HTTPUtils.newSetCookieHeader(
									"Realm-" + webApplication.getRealm(), 
									token.getName() + "/" + ((TokenWithSecret) token).getSecret(), 
									// if there is no valid until in the token, set it to a year
									token.getValidUntil() == null ? new Date(new Date().getTime() + 1000l*60*60*24*365) : token.getValidUntil(),
									// path
									webApplication.getCookiePath(), 
									// domain
									null,
									// secure
									sslOnly,
									// http only
									true
								));
							}
						}
						Session newSession = webApplication.getSessionProvider().newSession();
						// copy existing session
						if (session != null) {
							for (String key : session) {
								newSession.set(key, session.get(key));
							}
						}
						// set the new token
						newSession.set(GlueListener.buildTokenName(token.getRealm()), currentToken);
						ModifiableHeader cookieHeader = HTTPUtils.newSetCookieHeader(
							GlueListener.SESSION_COOKIE, 
							newSession.getId(), 
							null, 
							webApplication.getCookiePath(), 
							null, 
							sslOnly, 
							true
						);
						response.getContent().setHeader(cookieHeader);
					}
					
					if (interceptor != null) {
						response = (HTTPResponse) interceptor.intercept(response);
					}
					
					return response;
				}
			}
		}
		catch (FormatException e) {
			if (isForThisService) {
				toggleLogging(request, e);
			}
			HTTPException httpException = new HTTPException(500, "Could not execute service", "Could not execute service: " + service.getId(), e, token);
			httpException.getContext().addAll(Arrays.asList(webApplication.getId(), service.getId()));
			httpException.setDevice(device);
			throw httpException;
		}
		catch (IOException e) {
			if (isForThisService) {
				toggleLogging(request, e);
			}
			HTTPException httpException = new HTTPException(500, "Could not execute service", "Could not execute service: " + service.getId(), e, token);
			httpException.getContext().addAll(Arrays.asList(webApplication.getId(), service.getId()));
			httpException.setDevice(device);
			throw httpException;
		}
		catch (ServiceException e) {
			if (isForThisService) {
				toggleLogging(request, e);
			}
			HTTPException httpException;
			if (ServiceRuntime.NO_AUTHORIZATION.equals(e.getCode())) {
				httpException = new HTTPException(403, "User does not have permission to execute the rest service", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to '" + request.getMethod().toLowerCase() + "' with service: " + service.getId(), e, token);
			}
			else if (ServiceRuntime.NO_AUTHENTICATION.equals(e.getCode())) {
				httpException = new HTTPException(401, "User is not authenticated", e, token);
			}
			// this is the code thrown by the flow service for validation errors
			else if ("VM-4".equals(e.getCode())) {
				httpException = new HTTPException(400, "Input validation failed", e, token);
			}
			else {
				httpException = new HTTPException(500, "Could not execute service", "Could not execute service: " + service.getId(), e, token);
			}
			httpException.getContext().addAll(Arrays.asList(webApplication.getId(), service.getId()));
			httpException.setDevice(device);
			throw httpException;
		}
		catch (HTTPException e) {
			if (isForThisService) {
				toggleLogging(request, e);
			}
			if (e.getToken() == null) {
				e.setToken(token);
			}
			if (e.getDevice() == null) {
				e.setDevice(device);
			}
			e.getContext().addAll(Arrays.asList(webApplication.getId(), service.getId()));
			throw e;
		}
		catch (Exception e) {
			if (isForThisService) {
				toggleLogging(request, e);
			}
			HTTPException httpException = new HTTPException(500, "Could not execute service", "Could not execute service: " + service.getId(), e, token);
			httpException.getContext().addAll(Arrays.asList(webApplication.getId(), service.getId()));
			httpException.setDevice(device);
			throw httpException;
		}
		finally {
			// make sure we log the successful
			if (isForThisService) {
				toggleLogging(request, null);
			}
			ServiceRuntime.setGlobalContext(null);
		}
	}
	
	private void toggleLogging(HTTPRequest request, Throwable exception) {
		if ((exception != null && webArtifact.getConfig().isCaptureErrors()) || (exception == null && webArtifact.getConfig().isCaptureSuccessful())) {
			if (service != null && MimeUtils.getHeader("X-Nabu-Log", request.getContent().getHeaders()) == null) {
				try {
					request.getContent().setHeader(MimeHeader.parseHeader("X-Nabu-Log: " + (exception == null ? "info" : "error") + ";artifactId=" + service.getId() + ";rawHttp=true"));
				}
				catch (Exception e) {
					logger.warn("Could not set logging header", e);
				}
			}
		}
	}
	
	private void report(HTTPRequest request, Exception e, Token token) {
		HTTPFormatter formatter = new HTTPFormatter();
		// do not allow binary, we are stringifying the request
		formatter.getFormatter().setAllowBinary(false);
		// do not allow cookies to be stored, for GDPR reasons (they might contain identifiable information)
		formatter.getFormatter().ignoreHeaders("Cookie");
		formatter.getFormatter().ignoreHeaders("Authorization");
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
		notification.setCode("HTTP-" + (e instanceof HTTPException ? ((HTTPException) e).getCode() : 0));
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
