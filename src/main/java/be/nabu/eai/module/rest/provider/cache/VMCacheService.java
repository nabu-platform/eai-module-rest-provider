package be.nabu.eai.module.rest.provider.cache;

import java.util.Date;

import be.nabu.eai.repository.ServiceInterfaceFromDefinedService;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;

public class VMCacheService extends SimpleVMServiceDefinition {

	public VMCacheService(Pipeline pipeline) {
		super(pipeline);
	}
	
	public VMCacheService(DefinedService parent) {
		super(new Pipeline(new Structure(), new Structure()));
		// set the service to inherit from
		getPipeline().setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), new CacheInterface(new ServiceInterfaceFromDefinedService(parent))));
	}
	
	public static class CacheInterface implements DefinedServiceInterface {
		
		private final DefinedServiceInterface iface;
		private Structure output, input;

		public CacheInterface(DefinedServiceInterface iface) {
			this.iface = iface;
		}

		@Override
		public ComplexType getInputDefinition() {
			if (input == null) {
				input = new Structure();
				input.setName("input");
				input.setSuperType(iface.getInputDefinition());
				Structure clientCache = new Structure();
				clientCache.setName("clientCache");
				clientCache.add(new SimpleElementImpl<Date>("lastModified", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Date.class), clientCache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				clientCache.add(new SimpleElementImpl<String>("etag", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), clientCache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				input.add(new ComplexElementImpl("clientCache", clientCache, input));
				
				Structure serverCache = new Structure();
				serverCache.setName("serverCache");
				serverCache.add(new SimpleElementImpl<Date>("lastModified", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Date.class), serverCache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				serverCache.add(new SimpleElementImpl<String>("hash", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), serverCache, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				// could be that there is no server cache or it is not inspectable enough, so everything is optional
				input.add(new ComplexElementImpl("serverCache", serverCache, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
			}
			return input;
		}

		@Override
		public ComplexType getOutputDefinition() {
			if (output == null) {
				output = new Structure();
				output.setName("output");
				output.add(new SimpleElementImpl<Boolean>("hasChanged", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), output));
				output.add(new SimpleElementImpl<Integer>("maxAge", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Integer.class), output,
					new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
					new ValueImpl<String>(CommentProperty.getInstance(), "The timeout of this cache expressed in seconds")
				));
			}
			return output;
		}

		@Override
		public ServiceInterface getParent() {
			return null;
		}

		@Override
		public String getId() {
			return iface.getId();
		}
	}
	
}
