package be.nabu.eai.module.rest.provider.iface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.BaseNodeMetadataArtifactFragmentManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.definition.xml.XMLDefinitionMarshaller;
import be.nabu.libs.types.properties.AliasProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;

public class RESTInterfaceArtifactFragmentManager extends BaseNodeMetadataArtifactFragmentManager<RESTInterfaceArtifact> implements CreatableArtifactFragmentManager<RESTInterfaceArtifact> {

	private static final Logger logger = LoggerFactory.getLogger(RESTInterfaceArtifactFragmentManager.class);
	private static final String REST_INTERFACE_PATH = "rest-interface.xml";
	private static final String ARTIFACT_RESOURCE_PATH = "webrestartifact.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "restInterface";
	private static final String GUIDELINES_PATH = "/guidelines/rest-interface.md";
	protected static final String QUERY = "query";
	protected static final String COOKIE = "cookie";
	protected static final String SESSION = "session";
	protected static final String HEADER = "header";
	protected static final String RESPONSE_HEADER = "responseHeader";
	protected static final String PATH_PARAMETERS = "pathParameters";
	protected static final String PERMISSION_CONTEXT_TYPE = "permissionContextType";

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new RESTInterfaceManager(), true);
			RESTInterfaceArtifact artifact = new RESTInterfaceArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
			new RESTInterfaceManager().save(entry, artifact);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ArtifactFragment> listFragments(final RESTInterfaceArtifact artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(getSharedFragments(artifact));
		fragments.add(new ArtifactFragment() {
			@Override
			public boolean isEditable() {
				return EAIResourceRepository.getInstance().getEntry(artifact.getId()) instanceof ResourceEntry;
			}

			@Override
			public boolean isRemovable() {
				return false;
			}

			@Override
			public String getPath() {
				return REST_INTERFACE_PATH;
			}

			@Override
			public String getContent() {
				try {
					return marshalFragment(artifact);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public String getContentType() {
				return CONTENT_TYPE;
			}

			@Override
			public String getArtifactId() {
				return artifact.getId();
			}

			@Override
			public String getFragmentType() {
				return ARTIFACT_TYPE;
			}

			@Override
			public Map<String, String> getProperties() {
				return new LinkedHashMap<String, String>();
			}

			@Override
			public Long getLastModified() {
				return getFragmentLastModified(artifact.getId(), ARTIFACT_RESOURCE_PATH);
			}
		});
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(RESTInterfaceArtifact artifact, String path, String oldContent, String newContent) {
		if (!REST_INTERFACE_PATH.equals(path)) {
			return super.updateFragment(artifact, path, oldContent, newContent);
		}
		ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
		List<Validation<?>> validations = applyFragment(entry, artifact, path, oldContent, newContent);
		if (!hasErrors(validations)) {
			try {
				validations.addAll(new RESTInterfaceManager().save(entry, artifact));
			}
			catch (Exception e) {
				logger.error("Failed to save REST interface fragment for artifact: " + artifact.getId(), e);
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
			}
		}
		return validations;
	}

	public List<Validation<?>> applyFragment(ResourceEntry entry, RESTInterfaceArtifact artifact, String path, String oldContent, String newContent) {
		if (!REST_INTERFACE_PATH.equals(path)) {
			throw new UnsupportedOperationException("Updating fragments is only supported for rest-interface.xml on REST interfaces");
		}
		return updateRESTFragment(artifact, newContent, entry);
	}

	private List<Validation<?>> updateRESTFragment(RESTInterfaceArtifact artifact, String newContent, ResourceEntry entry) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		try {
//			RESTInterfaceArtifact updated = new RESTInterfaceManager().load(entry, validations);
			if (hasErrors(validations)) {
				return validations;
			}
			Document document = parseDocument(newContent);
			String rawConfig = stripEmbeddedStructures(document);
			RESTInterfaceConfiguration parsedConfig = parseUpdatedConfig(entry, rawConfig);
			preserveHiddenFields(artifact.getConfig(), parsedConfig);
			applyPermissionContextType(document, parsedConfig, validations);
			if (hasErrors(validations)) {
				return validations;
			}
			copyConfig(parsedConfig, artifact.getConfig());
			artifact.setQueryParameters(parseEmbeddedStructure(entry, artifact.getQueryParameters(), findStructureXml(document, QUERY), QUERY, validations));
			artifact.setCookie(parseEmbeddedStructure(entry, artifact.getCookie(), findStructureXml(document, COOKIE), COOKIE, validations));
			artifact.setSession(parseEmbeddedStructure(entry, artifact.getSession(), findStructureXml(document, SESSION), SESSION, validations));
			artifact.setRequestHeaderParameters(parseEmbeddedStructure(entry, artifact.getRequestHeaderParameters(), findStructureXml(document, HEADER), HEADER, validations));
			artifact.setPathParameters(parseEmbeddedStructure(entry, artifact.getPathParameters(), findStructureXml(document, PATH_PARAMETERS), "path", validations));
			artifact.setResponseHeaderParameters(parseEmbeddedStructure(entry, artifact.getResponseHeaderParameters(), findStructureXml(document, RESPONSE_HEADER), RESPONSE_HEADER, validations));
			if (hasErrors(validations)) {
				return validations;
			}
			artifact.getConfig().setQueryParameters(toConfiguredNames(artifact.getQueryParameters()));
			artifact.getConfig().setCookieParameters(toConfiguredNames(artifact.getCookie()));
			artifact.getConfig().setSessionParameters(toConfiguredNames(artifact.getSession()));
			artifact.getConfig().setHeaderParameters(toConfiguredNames(artifact.getRequestHeaderParameters()));
			artifact.getConfig().setResponseHeaders(toConfiguredNames(artifact.getResponseHeaderParameters()));
			validations.addAll(new RESTInterfaceManager().save(entry, artifact));
		}
		catch (Exception e) {
			logger.error("Failed to update REST interface fragment for artifact: " + artifact.getId(), e);
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	private String marshalFragment(RESTInterfaceArtifact artifact) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		artifact.marshal(artifact.getConfig(), output);
		Document document = parseDocument(new String(output.toByteArray(), StandardCharsets.UTF_8));
		Element root = document.getDocumentElement();
		appendStructure(document, root, artifact.getQueryParameters(), QUERY);
		appendStructure(document, root, artifact.getCookie(), COOKIE);
		appendStructure(document, root, artifact.getSession(), SESSION);
		appendStructure(document, root, artifact.getRequestHeaderParameters(), HEADER);
		appendStructure(document, root, artifact.getPathParameters(), PATH_PARAMETERS);
		appendStructure(document, root, artifact.getResponseHeaderParameters(), RESPONSE_HEADER);
		appendPermissionContextType(document, root, artifact.getConfig());
		removeDirectChild(root, "queryParameters");
		removeDirectChild(root, "cookieParameters");
		removeDirectChild(root, "sessionParameters");
		removeDirectChild(root, "headerParameters");
		removeDirectChild(root, "responseHeaders");
		removeDirectChild(root, PATH_PARAMETERS);
		return toXml(document);
	}

	protected RESTInterfaceConfiguration parseUpdatedConfig(ResourceEntry entry, String content) throws Exception {
		RESTInterfaceArtifact parsed = new RESTInterfaceArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		return parsed.unmarshal(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	protected Structure parseEmbeddedStructure(ResourceEntry entry, Structure current, String xml, String rootName, List<Validation<?>> validations) throws Exception {
		Structure parsed = StructureManager.parseUpdatedStructure(entry, xml, current, new Structure(), validations);
		parsed.setName(rootName);
		return parsed;
	}

	protected String toConfiguredNames(Structure structure) {
		List<String> names = new ArrayList<String>();
		for (be.nabu.libs.types.api.Element<?> child : structure) {
			be.nabu.libs.property.api.Value<String> alias = child.getProperty(AliasProperty.getInstance());
			names.add(alias == null || alias.getValue() == null || alias.getValue().trim().isEmpty() ? child.getName() : alias.getValue());
		}
		return names.isEmpty() ? null : String.join(",", names);
	}

	private void appendStructure(Document document, Element root, Structure structure, String name) throws Exception {
		Element previous = getDirectChild(root, name);
		if (previous != null) {
			root.removeChild(previous);
		}
		String xml = marshalStructure(structure, name);
		Element parsed = parseDocument(xml).getDocumentElement();
		root.appendChild(document.importNode(parsed, true));
	}

	private String marshalStructure(Structure structure, String name) throws Exception {
		Structure embedded = new Structure();
		embedded.setName(name);
		if (structure != null) {
			for (be.nabu.libs.types.api.Element<?> child : structure) {
				embedded.add(child);
			}
			StructureManager.inheritRootProperties(structure, embedded);
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
		marshaller.setIgnoreUnknownSuperTypes(true);
		marshaller.marshal(output, embedded);
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	protected Document parseDocument(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(false);
		factory.setCoalescing(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	protected String stripEmbeddedStructures(Document document) throws Exception {
		Element root = document.getDocumentElement();
		removeDirectChild(root, QUERY);
		removeDirectChild(root, COOKIE);
		removeDirectChild(root, SESSION);
		removeDirectChild(root, HEADER);
		removeDirectChild(root, PATH_PARAMETERS);
		removeDirectChild(root, RESPONSE_HEADER);
		removeDirectChild(root, PERMISSION_CONTEXT_TYPE);
		return toXml(document);
	}

	protected String findStructureXml(Document document, String name) throws Exception {
		Element child = getDirectChild(document.getDocumentElement(), name);
		if (child == null) {
			return marshalStructure(null, name);
		}
		Document single = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		single.appendChild(single.importNode(child, true));
		return toXml(single);
	}

	private Element getDirectChild(Element root, String name) {
		NodeList childNodes = root.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node child = childNodes.item(i);
			if (child instanceof Element && name.equals(((Element) child).getTagName())) {
				return (Element) child;
			}
		}
		return null;
	}

	private void removeDirectChild(Element root, String name) {
		Element child = getDirectChild(root, name);
		if (child != null) {
			root.removeChild(child);
		}
	}

	private String toXml(Document document) throws Exception {
		removeWhitespaceNodes(document);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		EAIRepositoryUtils.prettyPrint(document, output);
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	private void removeWhitespaceNodes(Node node) {
		NodeList childNodes = node.getChildNodes();
		for (int i = childNodes.getLength() - 1; i >= 0; i--) {
			Node child = childNodes.item(i);
			if (child.getNodeType() == Node.TEXT_NODE && (child.getTextContent() == null || child.getTextContent().trim().isEmpty())) {
				node.removeChild(child);
			}
			else {
				removeWhitespaceNodes(child);
			}
		}
	}

	protected void preserveHiddenFields(RESTInterfaceConfiguration current, RESTInterfaceConfiguration parsed) {
		parsed.setAsynchronous(current.getAsynchronous());
		parsed.setSanitizeInput(current.getSanitizeInput());
	}

	private void appendPermissionContextType(Document document, Element root, RESTInterfaceConfiguration config) {
		String value = getPermissionContextType(config);
		if (value == null) {
			removeDirectChild(root, PERMISSION_CONTEXT_TYPE);
			return;
		}
		Element existing = getDirectChild(root, PERMISSION_CONTEXT_TYPE);
		if (existing == null) {
			existing = document.createElement(PERMISSION_CONTEXT_TYPE);
			root.appendChild(existing);
		}
		existing.setTextContent(value);
	}

	protected void applyPermissionContextType(Document document, RESTInterfaceConfiguration config, List<Validation<?>> validations) {
		Element element = getDirectChild(document.getDocumentElement(), PERMISSION_CONTEXT_TYPE);
		if (element == null) {
			return;
		}
		String value = element.getTextContent() == null ? null : element.getTextContent().trim();
		config.setUseServiceContextAsPermissionContext(false);
		config.setUseWebApplicationAsPermissionContext(false);
		config.setUseProjectAsPermissionContext(false);
		config.setUseGlobalPermissionContext(false);
		if (value == null || value.isEmpty()) {
			return;
		}
		if ("SERVICE_CONTEXT".equals(value)) {
			config.setUseServiceContextAsPermissionContext(true);
		}
		else if ("WEB_APPLICATION".equals(value)) {
			config.setUseWebApplicationAsPermissionContext(true);
		}
		else if ("PROJECT".equals(value)) {
			config.setUseProjectAsPermissionContext(true);
		}
		else if ("GLOBAL".equals(value)) {
			config.setUseGlobalPermissionContext(true);
		}
		else {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Unsupported permissionContextType: " + value));
		}
	}

	private String getPermissionContextType(RESTInterfaceConfiguration config) {
		if (config.getPermissionContext() != null && !config.getPermissionContext().trim().isEmpty()) {
			return null;
		}
		if (config.isUseServiceContextAsPermissionContext()) {
			return "SERVICE_CONTEXT";
		}
		if (config.isUseWebApplicationAsPermissionContext()) {
			return "WEB_APPLICATION";
		}
		if (config.isUseProjectAsPermissionContext()) {
			return "PROJECT";
		}
		if (config.isUseGlobalPermissionContext()) {
			return "GLOBAL";
		}
		return null;
	}

	protected void copyConfig(RESTInterfaceConfiguration source, RESTInterfaceConfiguration target) {
		target.setMethod(source.getMethod());
		target.setPath(source.getPath());
		target.setQueryParameters(source.getQueryParameters());
		target.setCookieParameters(source.getCookieParameters());
		target.setSessionParameters(source.getSessionParameters());
		target.setHeaderParameters(source.getHeaderParameters());
		target.setResponseHeaders(source.getResponseHeaders());
		target.setRoles(source.getRoles());
		target.setPermissionAction(source.getPermissionAction());
		target.setPermissionContext(source.getPermissionContext());
		target.setUseServiceContextAsPermissionContext(source.isUseServiceContextAsPermissionContext());
		target.setUseWebApplicationAsPermissionContext(source.isUseWebApplicationAsPermissionContext());
		target.setUseProjectAsPermissionContext(source.isUseProjectAsPermissionContext());
		target.setUseGlobalPermissionContext(source.isUseGlobalPermissionContext());
		target.setPreferredResponseType(source.getPreferredResponseType());
		target.setAsynchronous(source.getAsynchronous());
		target.setInputAsStream(source.getInputAsStream());
		target.setOutputAsStream(source.getOutputAsStream());
		target.setInput(source.getInput());
		target.setOutput(source.getOutput());
		target.setSanitizeInput(source.getSanitizeInput());
		target.setAcceptedLanguages(source.getAcceptedLanguages());
		target.setConfigurationType(source.getConfigurationType());
		target.setDevice(source.getDevice());
		target.setDeviceBestEffort(source.isDeviceBestEffort());
		target.setToken(source.getToken());
		target.setLenient(source.getLenient());
		target.setNamingConvention(source.getNamingConvention());
		target.setWebApplicationId(source.isWebApplicationId());
		target.setGeoPosition(source.isGeoPosition());
		target.setUseAsAuthorizationServiceContext(source.isUseAsAuthorizationServiceContext());
		target.setLanguage(source.isLanguage());
		target.setAllowFormBinding(source.isAllowFormBinding());
		target.setCaseInsensitive(source.isCaseInsensitive());
		target.setCache(source.isCache());
		target.setAllowCookiesWithoutReferer(source.isAllowCookiesWithoutReferer());
		target.setAllowCookiesWithExternalReferer(source.isAllowCookiesWithExternalReferer());
		target.setRequest(source.isRequest());
		target.setAllowHeaderAsQueryParameter(source.isAllowHeaderAsQueryParameter());
		target.setUseServerCache(source.isUseServerCache());
		target.setSource(source.isSource());
		target.setAllowRaw(source.isAllowRaw());
		target.setDomain(source.isDomain());
		target.setOrigin(source.isOrigin());
		target.setScheme(source.isScheme());
		target.setTemporaryAlias(source.getTemporaryAlias());
		target.setTemporarySecret(source.getTemporarySecret());
		target.setTemporaryCorrelationId(source.getTemporaryCorrelationId());
		target.setRateLimitContext(source.getRateLimitContext());
		target.setRateLimitAction(source.getRateLimitAction());
		target.setIgnoreOffline(source.isIgnoreOffline());
		target.setAllowRootArrays(source.isAllowRootArrays());
		target.setCaptureErrors(source.isCaptureErrors());
		target.setCaptureSuccessful(source.isCaptureSuccessful());
		target.setParent(source.getParent());
		target.setLimitedToInterface(source.isLimitedToInterface());
		target.setAllowExplicitResponseCode(source.isAllowExplicitResponseCode());
		target.setStubbed(source.isStubbed());
		target.setClusterLock(source.isClusterLock());
	}

	protected boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	@Override
	public List<Validation<?>> deleteFragment(RESTInterfaceArtifact artifact, String path) {
		throw new UnsupportedOperationException("Deleting fragments is not supported for REST interfaces");
	}

	@Override
	public List<Validation<?>> createFragment(RESTInterfaceArtifact artifact, String path, String content) {
		throw new UnsupportedOperationException("Creating fragments is not supported for REST interfaces");
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> sections = new ArrayList<String>();
		if (fragmentTypes == null || fragmentTypes.isEmpty() || fragmentTypes.contains(ARTIFACT_TYPE) || fragmentTypes.contains(REST_INTERFACE_PATH) || fragmentTypes.contains(ARTIFACT_RESOURCE_PATH)) {
			sections.add(loadGuidelinesResource(GUIDELINES_PATH));
		}
		String metadataGuidance = super.getGuidelines(Arrays.asList("metadata"));
		if (metadataGuidance != null && !metadataGuidance.trim().isEmpty()) {
			sections.add(metadataGuidance.trim());
		}
		return sections.isEmpty() ? null : String.join("\n\n", sections).trim();
	}

	private String loadGuidelinesResource(String resourcePath) {
		return EAIRepositoryUtils.loadCachedClasspathResource(RESTInterfaceArtifactFragmentManager.class, resourcePath);
	}

	@Override
	public Class<RESTInterfaceArtifact> getArtifactClass() {
		return RESTInterfaceArtifact.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return "service";
	}
}
