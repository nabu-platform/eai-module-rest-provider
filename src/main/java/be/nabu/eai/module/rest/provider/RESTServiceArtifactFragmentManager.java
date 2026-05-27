package be.nabu.eai.module.rest.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;

import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceConfiguration;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifactFragmentManager;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceManager;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Node;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.impl.BaseNodeMetadataArtifactFragmentManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.repository.artifacts.container.ContainerArtifactManager.ContainerRepository;
import be.nabu.eai.repository.artifacts.container.ContainerArtifactManager.WrapperEntry;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.eai.module.services.vm.VMServiceManager;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.definition.xml.XMLDefinitionMarshaller;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.utils.io.IOUtils;

public class RESTServiceArtifactFragmentManager extends BaseNodeMetadataArtifactFragmentManager<RESTService> implements CreatableArtifactFragmentManager<RESTService> {

	private static final String REST_INTERFACE_PATH = "rest-interface.xml";
	private static final String GUIDELINES_PATH = "/guidelines/rest-service.md";
	private static final String PIPELINE_PATH = "pipeline.xml";
	private static final String SERVICE_PATH = "service.xml";
	private static final String INPUT_PATH = "input.xml";
	private static final String OUTPUT_PATH = "output.xml";
	private static final String ARTIFACT_TYPE = "restService";

	private final RESTInterfaceArtifactFragmentManager restInterfaceFragmentManager = new RESTInterfaceArtifactFragmentManager();
	private final be.nabu.eai.module.services.vm.VMServiceArtifactFragmentManager vmServiceFragmentManager = new be.nabu.eai.module.services.vm.VMServiceArtifactFragmentManager();
	private final be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager<RESTService> definedServiceFragmentManager = new be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager<RESTService>();

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new RESTServiceManager(), true);
			RESTService artifact = RESTServiceManager.createDefaultService(entry.getId(), entry.getContainer(), entry.getRepository());
			new RESTServiceManager().save(entry, artifact);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ArtifactFragment> listFragments(RESTService artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(getSharedFragments(artifact));
		Entry serviceEntry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		boolean editable = serviceEntry != null && serviceEntry.isEditable();
		RESTInterfaceArtifact api = artifact.getArtifact("api");
		if (api != null) {
			for (ArtifactFragment fragment : restInterfaceFragmentManager.listFragments(api)) {
				if (REST_INTERFACE_PATH.equals(fragment.getPath())) {
					fragments.add(new DelegatingFragment(artifact, fragment, REST_INTERFACE_PATH, editable));
				}
			}
		}
		SimpleVMServiceDefinition implementation = artifact.getArtifact("implementation");
		if (implementation != null) {
			fragments.add(new ContainerPipelineFragment(artifact, implementation, editable));
			fragments.add(new ContainerServiceFragment(artifact, editable));
			fragments.add(new RestServiceContractFragment(artifact, api, INPUT_PATH));
			fragments.add(new RestServiceContractFragment(artifact, api, OUTPUT_PATH));
		}
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(RESTService artifact, String path, String oldContent, String newContent) {
		if (REST_INTERFACE_PATH.equals(path)) {
			RESTInterfaceArtifact api = artifact.getArtifact("api");
			if (api == null) {
				return Collections.<Validation<?>>emptyList();
			}
			return saveContainerArtifactIfValid(artifact, restInterfaceFragmentManager.applyFragment(getChildEntry(artifact, "api"), api, path, oldContent, newContent));
		}
		if (PIPELINE_PATH.equals(path) || SERVICE_PATH.equals(path)) {
			SimpleVMServiceDefinition implementation = artifact.getArtifact("implementation");
			if (implementation == null) {
				return Collections.<Validation<?>>emptyList();
			}
			WrapperEntry entry = getChildEntry(artifact, "implementation");
			if (PIPELINE_PATH.equals(path)) {
				return saveContainerArtifactIfValid(artifact, updateImplementationPipeline(entry, implementation, newContent));
			}
			return saveContainerArtifactIfValid(artifact, vmServiceFragmentManager.applyFragment(entry, implementation, path, oldContent, newContent));
		}
		if (INPUT_PATH.equals(path) || OUTPUT_PATH.equals(path)) {
			throw new UnsupportedOperationException("Updating input.xml and output.xml is not supported for REST services");
		}
		return super.updateFragment(artifact, path, oldContent, newContent);
	}

	@Override
	public List<Validation<?>> deleteFragment(RESTService artifact, String path) {
		throw new UnsupportedOperationException("Deleting fragments is not supported for REST services");
	}

	@Override
	public List<Validation<?>> createFragment(RESTService artifact, String path, String content) {
		throw new UnsupportedOperationException("Creating fragments is not supported for REST services");
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> sections = new ArrayList<String>();
		sections.add(loadGuidelinesResource(GUIDELINES_PATH));
		String metadataGuidance = super.getGuidelines(Arrays.asList("metadata"));
		if (metadataGuidance != null && !metadataGuidance.trim().isEmpty()) {
			sections.add(metadataGuidance.trim());
		}
		return String.join("\n\n", sections).trim();
	}

	private String loadGuidelinesResource(String resourcePath) {
		return be.nabu.eai.repository.EAIRepositoryUtils.loadCachedClasspathResource(RESTServiceArtifactFragmentManager.class, resourcePath);
	}

	@Override
	public Class<RESTService> getArtifactClass() {
		return RESTService.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return "service";
	}

	private class ContainerPipelineFragment implements ArtifactFragment {

		private final RESTService artifact;
		private final SimpleVMServiceDefinition implementation;
		private final boolean editable;

		private ContainerPipelineFragment(RESTService artifact, SimpleVMServiceDefinition implementation, boolean editable) {
			this.artifact = artifact;
			this.implementation = implementation;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public String getPath() {
			return PIPELINE_PATH;
		}

		@Override
		public String getContent() {
			try {
				XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
				marshaller.setIgnoreUnknownSuperTypes(true);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				marshaller.marshal(output, implementation.getPipeline());
				return new String(output.toByteArray(), "UTF-8");
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getContentType() {
			return "application/xml";
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return "pipeline";
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), "private/implementation/" + PIPELINE_PATH);
		}
	}

	private class RestServiceContractFragment implements ArtifactFragment {

		private final RESTService artifact;
		private final RESTInterfaceArtifact api;
		private final String path;

		private RestServiceContractFragment(RESTService artifact, RESTInterfaceArtifact api, String path) {
			this.artifact = artifact;
			this.api = api;
			this.path = path;
		}

		@Override
		public boolean isEditable() {
			return false;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getContent() {
			try {
				XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
				marshaller.setIgnoreUnknownSuperTypes(true);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				marshaller.marshal(output, INPUT_PATH.equals(path) ? buildRestServiceInput(api) : buildRestServiceOutput(api));
				return new String(output.toByteArray(), StandardCharsets.UTF_8);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getContentType() {
			return "application/xml";
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return "structure";
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}

		@Override
		public Long getLastModified() {
			return null;
		}
	}

	private class ContainerServiceFragment implements ArtifactFragment {

		private final RESTService artifact;
		private final boolean editable;

		private ContainerServiceFragment(RESTService artifact, boolean editable) {
			this.artifact = artifact;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public String getPath() {
			return SERVICE_PATH;
		}

		@Override
		public String getContent() {
			ResourceEntry entry = (ResourceEntry) be.nabu.eai.repository.EAIResourceRepository.getInstance().getEntry(artifact.getId());
			try {
				Resource resource = EAIRepositoryUtils.getResource(entry, "private/implementation/" + SERVICE_PATH, false);
				try (ResourceReadableContainer readable = new ResourceReadableContainer((ReadableResource) resource)) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					VMServiceManager.formatSequence(IOUtils.wrap(output), VMServiceManager.parseSequence(IOUtils.wrap(IOUtils.toBytes(readable), true)), true, Arrays.asList("id", "x", "y", "lineNumber"));
					return new String(output.toByteArray(), "UTF-8");
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getContentType() {
			return "application/xml";
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return SERVICE_PATH;
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), "private/implementation/" + SERVICE_PATH);
		}
	}

	private static class StringReadableResource implements ReadableResource {

		private final ResourceContainer<?> parent;
		private final String name;
		private final String contentType;
		private final String content;

		private StringReadableResource(ResourceContainer<?> parent, String name, String contentType, String content) {
			this.parent = parent;
			this.name = name;
			this.contentType = contentType;
			this.content = content;
		}

		@Override
		public be.nabu.utils.io.api.ReadableContainer<be.nabu.utils.io.api.ByteBuffer> getReadable() throws IOException {
			return IOUtils.wrap(content.getBytes(StandardCharsets.UTF_8), true);
		}

		@Override
		public String getContentType() {
			return contentType;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public ResourceContainer<?> getParent() {
			return parent;
		}
	}

	private List<Validation<?>> saveContainerArtifactIfValid(RESTService artifact, List<Validation<?>> validations) {
		if (!hasErrors(validations)) {
			try {
				validations.addAll(new RESTServiceManager().save((RepositoryEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId()), artifact));
			}
			catch (Exception e) {
				String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, message));
			}
		}
		return validations;
	}

	private List<Validation<?>> updateImplementationPipeline(WrapperEntry entry, SimpleVMServiceDefinition implementation, String newContent) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		try {
			Pipeline updated = StructureManager.parseUpdatedStructure(entry, newContent, implementation.getPipeline(), new Pipeline(null, null), validations);
			validateInterfaceOwnedPipeline(implementation.getPipeline(), updated, validations);
			if (!hasErrors(validations)) {
				vmServiceFragmentManager.copyPipeline(implementation.getPipeline(), updated);
			}
		}
		catch (Exception e) {
			String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, message));
		}
		return validations;
	}

	private WrapperEntry getChildEntry(RESTService artifact, String partName) {
		RepositoryEntry entry = (RepositoryEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
		ResourceContainer<?> target = getChildContainer(entry, partName);
		ContainerRepository repository = new ContainerRepository(entry.getId(), entry, artifact.getContainedArtifacts());
		return new WrapperEntry(repository, entry, target, partName);
	}

	private ResourceContainer<?> getChildContainer(RepositoryEntry entry, String partName) {
		if ("main".equals(partName)) {
			return entry.getContainer();
		}
		ResourceContainer<?> privateDirectory = (ResourceContainer<?>) entry.getContainer().getChild(EAIResourceRepository.PRIVATE);
		if (privateDirectory == null) {
			throw new IllegalStateException("Could not find private container for REST service: " + entry.getId());
		}
		Resource child = privateDirectory.getChild(partName);
		if (!(child instanceof ResourceContainer)) {
			throw new IllegalStateException("Could not find child container '" + partName + "' for REST service: " + entry.getId());
		}
		return (ResourceContainer<?>) child;
	}

	private Structure buildRestServiceInput(RESTInterfaceArtifact api) {
		Structure input = new Structure();
		input.setName("input");
		RESTInterfaceConfiguration config = api.getConfig();
		addInlineStructure(input, "query", api.getQueryParameters(), hasRequiredChildren(api.getQueryParameters()) ? 1 : 0);
		addInlineStructure(input, "header", api.getRequestHeaderParameters(), hasRequiredChildren(api.getRequestHeaderParameters()) ? 1 : 0);
		addInlineStructure(input, "session", api.getSession(), hasRequiredChildren(api.getSession()) ? 1 : 0);
		addInlineStructure(input, "cookie", api.getCookie(), hasRequiredChildren(api.getCookie()) ? 1 : 0);
		addInlineStructure(input, "path", api.getPathParameters(), hasChildren(api.getPathParameters()) ? 1 : null);
		if (config.getInputAsStream() != null && config.getInputAsStream()) {
			input.add(new SimpleElementImpl<java.io.InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(java.io.InputStream.class), input));
			Structure meta = new Structure();
			meta.setName("meta");
			meta.add(new SimpleElementImpl<String>("contentType", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			meta.add(new SimpleElementImpl<String>("fileName", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			input.add(new ComplexElementImpl("meta", meta, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
		}
		else if (config.getInput() instanceof ComplexType) {
			input.add(new ComplexElementImpl("content", (ComplexType) config.getInput(), input));
		}
		return input;
	}

	private Structure buildRestServiceOutput(RESTInterfaceArtifact api) {
		Structure output = new Structure();
		output.setName("output");
		RESTInterfaceConfiguration config = api.getConfig();
		addInlineStructure(output, "header", api.getResponseHeaderParameters(), hasRequiredChildren(api.getResponseHeaderParameters()) ? 1 : 0);
		if (config.getOutputAsStream() != null && config.getOutputAsStream()) {
			output.add(new SimpleElementImpl<java.io.InputStream>("content", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(java.io.InputStream.class), output));
			Structure meta = new Structure();
			meta.setName("meta");
			meta.add(new SimpleElementImpl<String>("contentType", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			meta.add(new SimpleElementImpl<Long>("contentLength", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			meta.add(new SimpleElementImpl<String>("fileName", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), meta, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			output.add(new ComplexElementImpl("meta", meta, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
		}
		else if (config.getOutput() instanceof ComplexType) {
			output.add(new ComplexElementImpl("content", (ComplexType) config.getOutput(), output));
		}
		return output;
	}

	private void addInlineStructure(Structure parent, String name, Structure source, Integer minOccurs) {
		if (!hasChildren(source)) {
			return;
		}
		Structure structure = cloneStructure(source, name);
		if (minOccurs == null) {
			parent.add(new ComplexElementImpl(name, structure, parent));
		}
		else {
			parent.add(new ComplexElementImpl(name, structure, parent, new ValueImpl<Integer>(MinOccursProperty.getInstance(), minOccurs)));
		}
	}

	private Structure cloneStructure(Structure source, String name) {
		Structure target = new Structure();
		target.setName(name);
		for (Element<?> child : source) {
			target.add(be.nabu.libs.types.base.TypeBaseUtils.clone(child, target));
		}
		return target;
	}

	private boolean hasChildren(Structure structure) {
		return structure != null && !TypeUtils.getAllChildren(structure).isEmpty();
	}

	private boolean hasRequiredChildren(Structure structure) {
		if (structure == null) {
			return false;
		}
		for (Element<?> child : TypeUtils.getAllChildren(structure)) {
			be.nabu.libs.property.api.Value<Integer> minOccurs = child.getProperty(MinOccursProperty.getInstance());
			if (minOccurs == null || minOccurs.getValue() == null || minOccurs.getValue().intValue() > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	private void validateInterfaceOwnedPipeline(Pipeline original, Pipeline updated, List<Validation<?>> validations) {
		validateInterfaceOwnedPipeline("input", original == null ? null : (ComplexType) original.get(Pipeline.INPUT).getType(), updated == null ? null : (ComplexType) updated.get(Pipeline.INPUT).getType(), validations);
		validateInterfaceOwnedPipeline("output", original == null ? null : (ComplexType) original.get(Pipeline.OUTPUT).getType(), updated == null ? null : (ComplexType) updated.get(Pipeline.OUTPUT).getType(), validations);
	}

	private void validateInterfaceOwnedPipeline(String name, ComplexType original, ComplexType updated, List<Validation<?>> validations) {
		if (!sameStructure(original, updated)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "REST service " + name + " is controlled by rest-interface.xml and can not be changed through pipeline.xml"));
		}
	}

	private boolean sameStructure(ComplexType left, ComplexType right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		List<Element<?>> leftChildren = new ArrayList<Element<?>>(TypeUtils.getAllChildren(left));
		List<Element<?>> rightChildren = new ArrayList<Element<?>>(TypeUtils.getAllChildren(right));
		if (leftChildren.size() != rightChildren.size()) {
			return false;
		}
		for (int i = 0; i < leftChildren.size(); i++) {
			Element<?> leftChild = leftChildren.get(i);
			Element<?> rightChild = rightChildren.get(i);
			if (!sameElement(leftChild, rightChild)) {
				return false;
			}
		}
		return true;
	}

	private boolean sameElement(Element<?> left, Element<?> right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		if (!safeEquals(left.getName(), right.getName())) {
			return false;
		}
		if (!safeEquals(left.getType() == null ? null : left.getType().getClass().getName(), right.getType() == null ? null : right.getType().getClass().getName())) {
			return false;
		}
		if (!safeEquals(left.getType() == null ? null : left.getType().getName(), right.getType() == null ? null : right.getType().getName())) {
			return false;
		}
		if (!safeEquals(left.getType() == null ? null : left.getType().getNamespace(), right.getType() == null ? null : right.getType().getNamespace())) {
			return false;
		}
		if (!safeEquals(left.getProperties(), right.getProperties())) {
			return false;
		}
		if (left.getType() instanceof ComplexType || right.getType() instanceof ComplexType) {
			return left.getType() instanceof ComplexType && right.getType() instanceof ComplexType && sameStructure((ComplexType) left.getType(), (ComplexType) right.getType());
		}
		return true;
	}

	private boolean safeEquals(Object left, Object right) {
		return left == null ? right == null : left.equals(right);
	}

	private static class DelegatingFragment implements ArtifactFragment {

		private final RESTService artifact;
		private final ArtifactFragment delegate;
		private final String path;
		private final boolean editable;

		private DelegatingFragment(RESTService artifact, ArtifactFragment delegate, String path, boolean editable) {
			this.artifact = artifact;
			this.delegate = delegate;
			this.path = path;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getContent() {
			return delegate.getContent();
		}

		@Override
		public String getContentType() {
			return delegate.getContentType();
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return delegate.getFragmentType();
		}

		@Override
		public Map<String, String> getProperties() {
			return delegate.getProperties();
		}

		@Override
		public Long getLastModified() {
			return delegate.getLastModified();
		}
	}
}
