/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
