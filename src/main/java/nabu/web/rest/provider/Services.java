package nabu.web.rest.provider;

import java.util.ArrayList;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import be.nabu.eai.module.rest.provider.RESTService;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.authentication.api.Permission;

@WebService
public class Services {
	
	public static class PermissionImplementation implements Permission {
		private String permissionContext;
		private String permissionAction;
		public PermissionImplementation() {
			// auto-construct
		}
		public PermissionImplementation(String permissionContext, String permissionAction) {
			this.permissionContext = permissionContext;
			this.permissionAction = permissionAction;
		}
		@Override
		public String getContext() {
			return permissionContext;
		}
		@Override
		public String getAction() {
			return permissionAction;
		}
		public void setContext(String context) {
			this.permissionContext = context;
		}
		public void setAction(String action) {
			this.permissionAction = action;
		}
	}

	@WebResult(name = "permissions")
	public List<Permission> permissions(@WebParam(name = "id") String id) {
		List<Permission> permissions = new ArrayList<Permission>();
		List<RESTService> artifacts = EAIResourceRepository.getInstance().getArtifacts(RESTService.class);
		for (RESTService artifact : artifacts) {
			if (id == null || artifact.getId().equals(id) || artifact.getId().startsWith(id + ".")) {
				String permissionAction = artifact.getInterface().getConfig().getPermissionAction();
				String permissionContext = artifact.getInterface().getConfig().getPermissionContext();
				if (permissionAction != null) {
					permissions.add(new PermissionImplementation(permissionContext, permissionAction));
				}
			}
		}
		return permissions;
	}
}
