/*
 * Copyright 2012 LinkedIn Corp.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.webapp.servlet;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.user.Permission;
import azkaban.user.Role;
import azkaban.user.User;
import azkaban.user.UserManager;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.session.Session;

/**
 * The main page
 */
public class ProjectServlet extends LoginAbstractAzkabanServlet {
	private static final Logger logger =
			Logger.getLogger(ProjectServlet.class.getName());
	private static final String LOCKDOWN_CREATE_PROJECTS_KEY =
			"lockdown.create.projects";
	private static final long serialVersionUID = -1;

	private UserManager userManager;

	private boolean lockdownCreateProjects = false;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		AzkabanWebServer server = (AzkabanWebServer)getApplication();

		userManager = server.getUserManager();
		lockdownCreateProjects = server.getServerProps().getBoolean(
				LOCKDOWN_CREATE_PROJECTS_KEY, false);
		if (lockdownCreateProjects) {
			logger.info("Creation of projects is locked down");
		}
	}

	@Override
	protected void handleGet(HttpServletRequest req, HttpServletResponse resp,
			Session session) throws ServletException, IOException {
		if (hasParam(req, "doaction")) {
			if (getParam(req, "doaction").equals("search")) {
				String searchTerm = getParam(req, "searchterm");
				if (!searchTerm.equals("") && !searchTerm.equals(".*")) {
					handleFilter(req, resp, session, searchTerm);
					return;
				}
			}
		}

		User user = session.getUser();

		ProjectManager manager =
				((AzkabanWebServer)getApplication()).getProjectManager();
		Page page = newPage(
				req, resp, session, "azkaban/webapp/servlet/velocity/index.vm");

		if (lockdownCreateProjects && !hasPermissionToCreateProject(user)) {
			page.add("hideCreateProject", true);
		}

		List<Project> projects = manager.getProjects();

		/**
		 * Get project tags
		 */
		Set<String> projectTags = new HashSet<>();
		projects.stream()
                .map(Project::getMetadata)
                .filter(m -> !m.isEmpty())
                .map(m -> m.get("tags"))
                .filter(tags -> tags != null)
                .forEach(tags -> projectTags.addAll((List<String>) tags));

        page.add("projectTags", projectTags.stream().toArray(String[]::new));

		if (hasParam(req, "all")) {
			page.add("viewProjects", "all");
			page.add("projects", projects);
		}
		else if (hasParam(req, "untagged")) {
			List<Project> untaggedProjects = projects.stream().filter(p -> p.getMetadata().get("tags") == null).collect(Collectors.toList());
			page.add("viewProjects", "untagged");
			page.add("projects", untaggedProjects);
		}
        else if (hasParam(req, "tag")) {
            String tag = getParam(req, "tag");
            List<Project> tagMatchProjects = projects.stream().filter(p -> {
                List<String> tags = (List<String>) p.getMetadata().get("tags");
                return tags != null && tags.contains(tag);
            }).collect(Collectors.toList());
            page.add("viewProjects", "tag-" + tag);
            page.add("projects", tagMatchProjects);
        }
        else if (hasParam(req, "group")) {
			List<Project> groupProjects = manager.getGroupProjects(user);
			page.add("viewProjects", "group");
			page.add("projects", groupProjects);
		}
		else {
			List<Project> userProjects = manager.getUserProjects(user);
			page.add("viewProjects", "personal");
			page.add("projects", userProjects);
		}

		page.render();
	}

	private void handleFilter(HttpServletRequest req, HttpServletResponse resp,
			Session session, String searchTerm) {
		User user = session.getUser();
		ProjectManager manager =
				((AzkabanWebServer)getApplication()).getProjectManager();
		Page page = newPage(
				req, resp, session, "azkaban/webapp/servlet/velocity/index.vm");
		if (hasParam(req, "all")) {
			//do nothing special if one asks for 'ALL' projects
			List<Project> projects = manager.getProjectsByRegex(searchTerm);
			page.add("allProjects", "");
			page.add("projects", projects);
			page.add("search_term", searchTerm);
		}
		else {
			List<Project> projects = manager.getUserProjectsByRegex(user, searchTerm);
			page.add("projects", projects);
			page.add("search_term", searchTerm);
		}

		page.render();
	}

	@Override
	protected void handlePost(HttpServletRequest req, HttpServletResponse resp,
			Session session) throws ServletException, IOException {
		// TODO Auto-generated method stub
	}

	private boolean hasPermissionToCreateProject(User user) {
		for (String roleName: user.getRoles()) {
			Role role = userManager.getRole(roleName);
			Permission perm = role.getPermission();
			if (perm.isPermissionSet(Permission.Type.ADMIN) ||
					perm.isPermissionSet(Permission.Type.CREATEPROJECTS)) {
				return true;
			}
		}

		return false;
	}
}