package be.nabu.eai.module.rest.provider;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ContainerArtifactGUIManager;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.services.vm.VMServiceGUIManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.structure.Structure;

public class RESTServiceGUIManager extends ContainerArtifactGUIManager<RESTService> {

	public RESTServiceGUIManager() {
		super("REST Provider Service", RESTService.class, new RESTServiceManager());
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	public RESTService newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		RESTService restvmService = new RESTService(entry.getId());
		Map<String, String> configuration = new HashMap<String, String>();
		// add the id of the rest service
		configuration.put(VMServiceGUIManager.ACTUAL_ID, entry.getId());
		RESTInterfaceArtifact webRestArtifact = new RESTInterfaceArtifact("$self:api", entry.getContainer(), entry.getRepository());
		restvmService.addArtifact("api", webRestArtifact, configuration);
		Pipeline pipeline = new Pipeline(new Structure(), new Structure());
		pipeline.setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), webRestArtifact));
		SimpleVMServiceDefinition service = new SimpleVMServiceDefinition(pipeline);
		service.setId("$self:implementation");
		configuration.put(VMServiceGUIManager.INTERFACE_EDITABLE, "false");
		restvmService.addArtifact("implementation", service, configuration);
//		VMAuthorizationService authorization = new VMAuthorizationService(service);
//		authorization.setId(entry.getId() + ":security");
//		restvmService.addArtifact("security", authorization, configuration);
		return restvmService;
	}
	
	@Override
	public String getCategory() {
		return "Services";
	}
	
	@Override
	protected BaseArtifactGUIInstance<RESTService> newGUIInstance(Entry entry) {
		BaseArtifactGUIInstance<RESTService> newGUIInstance = super.newGUIInstance(entry);
		newGUIInstance.setRequiresPropertiesPane(true);
		return newGUIInstance;
	}
}
