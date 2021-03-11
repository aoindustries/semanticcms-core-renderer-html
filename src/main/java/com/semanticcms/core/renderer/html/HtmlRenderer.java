/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-renderer-html.
 *
 * semanticcms-core-renderer-html is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-renderer-html is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-renderer-html.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.renderer.html;

import com.aoindustries.encoding.MediaType;
import com.aoindustries.web.resources.servlet.RegistryEE;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.Link;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.renderer.Renderer;
import com.semanticcms.core.renderer.servlet.DefaultServletPageRenderer;
import com.semanticcms.core.renderer.servlet.ServletPageRenderer;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

/**
 * TODO: Consider custom EL resolver for this variable: http://stackoverflow.com/questions/5016965/how-to-add-a-custom-variableresolver-in-pure-jsp
 */
public class HtmlRenderer implements Renderer {

	// <editor-fold defaultstate="collapsed" desc="Singleton Instance (per application)">

	@WebListener("Registers the HtmlRenderer with SemanticCMS and exposes the HtmlRenderer as an application-scope variable \"" + APPLICATION_ATTRIBUTE + "\".")
	public static class Initializer implements ServletContextListener {

		private HtmlRenderer instance;

		@Override
		public void contextInitialized(ServletContextEvent event) {
			ServletContext servletContext = event.getServletContext();
			instance = getInstance(servletContext);
			SemanticCMS.getInstance(servletContext).addRenderer("", instance);
			// TODO: Register an export version at *.html, which redirects to not .html when not in export mode
		}

		@Override
		public void contextDestroyed(ServletContextEvent event) {
			if(instance != null) {
				instance.destroy();
				instance = null;
			}
			event.getServletContext().removeAttribute(APPLICATION_ATTRIBUTE);
		}
	}

	public static final String APPLICATION_ATTRIBUTE = "htmlRenderer";

	/**
	 * Gets the {@link HtmlRenderer} instance, creating it if necessary.
	 */
	public static HtmlRenderer getInstance(ServletContext servletContext) {
		HtmlRenderer htmlRenderer = (HtmlRenderer)servletContext.getAttribute(APPLICATION_ATTRIBUTE);
		if(htmlRenderer == null) {
			// TODO: Support custom implementations via context-param?
			htmlRenderer = new HtmlRenderer(servletContext);
			servletContext.setAttribute(APPLICATION_ATTRIBUTE, htmlRenderer);
		}
		return htmlRenderer;
	}

	private final ServletContext servletContext;

	protected HtmlRenderer(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	/**
	 * Called when the context is shutting down.
	 */
	protected void destroy() {
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Views">
	/**
	 * The parameter name used for views.
	 *
	 * TODO: Move to new Link type of Element in core-model?
	 */
	public static final String VIEW_PARAM = "view";

	private static class ViewsLock {}
	private final ViewsLock viewsLock = new ViewsLock();

	/**
	 * The views by name in order added.
	 */
	private final Map<String, View> viewsByName = new LinkedHashMap<>();

	private static final Set<View.Group> viewGroups = Collections.unmodifiableSet(EnumSet.allOf(View.Group.class));

	/**
	 * Gets all view groups.
	 */
	public Set<View.Group> getViewGroups() {
		return viewGroups;
	}

	/**
	 * Gets the views in order added.
	 */
	public Map<String, View> getViewsByName() {
		return Collections.unmodifiableMap(viewsByName);
	}

	/**
	 * The views in order.
	 */
	private final SortedSet<View> views = new TreeSet<>();

	/**
	 * Gets the views, ordered by view group then display.
	 *
	 * @see  View#compareTo(com.semanticcms.core.renderer.html.View)
	 */
	public SortedSet<View> getViews() {
		return Collections.unmodifiableSortedSet(views);
	}

	/**
	 * Registers a new view.
	 *
	 * @throws  IllegalStateException  if a view is already registered with the name.
	 */
	public void addView(View view) throws IllegalStateException {
		String name = view.getName();
		synchronized(viewsLock) {
			if(viewsByName.containsKey(name)) throw new IllegalStateException("View already registered: " + name);
			if(viewsByName.put(name, view) != null) throw new AssertionError();
			if(!views.add(view)) throw new AssertionError();
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Components">
	/**
	 * The components that are currently registered.
	 */
	private final List<Component> components = new CopyOnWriteArrayList<>();

	/**
	 * Gets all components in an undefined, but consistent (within a single run) ordering.
	 */
	public List<Component> getComponents() {
		return Collections.unmodifiableList(components);
	}

	/**
	 * Registers a new component.
	 */
	public void addComponent(Component component) {
		synchronized(components) {
			components.add(component);
			// Order the components by classname, just to have a consistent output
			// independent of the order components happened to be registered.
			Collections.sort(
				components,
				(o1, o2) -> o1.getClass().getName().compareTo(o2.getClass().getName())
			);
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Themes">
	/**
	 * The default theme is used when no other theme is registered.
	 */
	public static final String DEFAULT_THEME_NAME = "base";

	/**
	 * The themes in order added.
	 */
	private final Map<String, Theme> themes = new LinkedHashMap<>();

	/**
	 * Gets the themes, in the order added.
	 */
	public Map<String, Theme> getThemes() {
		synchronized(themes) {
			// Not returning a copy since themes are normally only registered on app start-up.
			return Collections.unmodifiableMap(themes);
		}
	}

	/**
	 * Registers a new theme.
	 *
	 * @throws  IllegalStateException  if a theme is already registered with the name.
	 */
	public void addTheme(Theme theme) throws IllegalStateException {
		String name = theme.getName();
		synchronized(themes) {
			if(themes.containsKey(name)) throw new IllegalStateException("Theme already registered: " + name);
			if(themes.put(name, theme) != null) throw new AssertionError();
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Scripts">
	/**
	 * The scripts in the order added.
	 */
	// TODO: RegistryEE
	private final Map<String, String> scripts = new LinkedHashMap<>();

	/**
	 * Gets the scripts, in the order added.
	 */
	// TODO: RegistryEE
	public Map<String, String> getScripts() {
		synchronized(scripts) {
			// Not returning a copy since scripts are normally only registered on app start-up.
			return Collections.unmodifiableMap(scripts);
		}
	}

	/**
	 * Registers a new script.  When a script is added multiple times,
	 * the src must be consistent between adds.  Also, a src may not be
	 * added under different names.
	 *
	 * @param  name  the name of the script, independent of version and src
	 * @param  src   the src of the script.
	 *
	 * @throws  IllegalStateException  if the script already registered but with a different src.
	 */
	// TODO: RegistryEE
	public void addScript(String name, String src) throws IllegalStateException {
		synchronized(scripts) {
			String existingSrc = scripts.get(name);
			if(existingSrc != null) {
				if(!src.equals(existingSrc)) {
					throw new IllegalStateException(
						"Script already registered but with a different src:"
						+ " name=" + name
						+ " src=" + src
						+ " existingSrc=" + existingSrc
					);
				}
			} else {
				// Make sure src not provided by another script
				if(scripts.values().contains(src)) {
					throw new IllegalArgumentException("Non-unique global script src: " + src);
				}
				if(scripts.put(name, src) != null) throw new AssertionError();
			}
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Head Includes">
	/**
	 * The head includes in the order added.
	 */
	private final Set<String> headIncludes = new LinkedHashSet<>();

	/**
	 * Gets the head includes, in the order added.
	 */
	public Set<String> getHeadIncludes() {
		synchronized(headIncludes) {
			// Not returning a copy since head includes are normally only registered on app start-up.
			return Collections.unmodifiableSet(headIncludes);
		}
	}

	/**
	 * Registers a new head include.
	 *
	 * @throws  IllegalStateException  if the link is already registered.
	 */
	public void addHeadInclude(String headInclude) throws IllegalStateException {
		synchronized(headIncludes) {
			if(!headIncludes.add(headInclude)) throw new IllegalStateException("headInclude already registered: " + headInclude);
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Links to Elements">

	/**
	 * Resolves the link CSS class for the given types of elements.
	 */
	@FunctionalInterface
	public static interface LinkCssClassResolver<E extends com.semanticcms.core.model.Element> {
		/**
		 * Gets the CSS class to use in links to the given element.
		 * When null is returned, any resolvers for super classes will also be invoked.
		 *
		 * @return  The CSS class name or {@code null} when none configured for the provided element.
		 */
		String getCssLinkClass(E element);
	}

	/**
	 * The CSS classes used in links.
	 */
	private final Map<Class<? extends com.semanticcms.core.model.Element>, LinkCssClassResolver<?>> linkCssClassResolverByElementType = new LinkedHashMap<>();

	/**
	 * Gets the CSS class to use in links to the given element.
	 * Also looks for match on parent classes up to and including Element itself.
	 * 
	 * @return  The CSS class or {@code null} when element is null or no class registered for it or any super class.
	 *
	 * @see  LinkCssClassResolver#getCssLinkClass(com.semanticcms.core.model.Element)
	 */
	public <E extends com.semanticcms.core.model.Element> String getLinkCssClass(E element) {
		if(element == null) return null;
		Class<? extends com.semanticcms.core.model.Element> elementType = element.getClass();
		synchronized(linkCssClassResolverByElementType) {
			while(true) {
				@SuppressWarnings("unchecked")
				LinkCssClassResolver<? super E> linkCssClassResolver = (LinkCssClassResolver<? super E>)linkCssClassResolverByElementType.get(elementType);
				if(linkCssClassResolver != null) {
					String linkCssClass = linkCssClassResolver.getCssLinkClass(element);
					if(linkCssClass != null) return linkCssClass;
				}
				if(elementType == com.semanticcms.core.model.Element.class) return null;
				elementType = elementType.getSuperclass().asSubclass(com.semanticcms.core.model.Element.class);
			}
		}
	}

	/**
	 * Registers a new CSS resolver to use in link to the given type of element.
	 *
	 * @throws  IllegalStateException  if the element type is already registered.
	 */
	public <E extends com.semanticcms.core.model.Element> void addLinkCssClassResolver(
		Class<E> elementType,
		LinkCssClassResolver<? super E> cssLinkClassResolver
	) throws IllegalStateException {
		synchronized(linkCssClassResolverByElementType) {
			if(linkCssClassResolverByElementType.containsKey(elementType)) throw new IllegalStateException("Link CSS class already registered: " + elementType);
			if(linkCssClassResolverByElementType.put(elementType, cssLinkClassResolver) != null) throw new AssertionError();
		}
	}

	/**
	 * Registers a new CSS class to use in link to the given type of element.
	 *
	 * @throws  IllegalStateException  if the element type is already registered.
	 */
	// TODO: Take a set of Group.Name activations, too
	public <E extends com.semanticcms.core.model.Element> void addLinkCssClass(
		Class<E> elementType,
		final String cssLinkClass
	) throws IllegalStateException {
		addLinkCssClassResolver(
			elementType,
			(E element) -> cssLinkClass
		);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Lists of Nodes">

	/**
	 * Resolves the list item CSS class for the given types of nodes.
	 */
	@FunctionalInterface
	public static interface ListItemCssClassResolver<N extends com.semanticcms.core.model.Node> {
		/**
		 * Gets the CSS class to use in list items to the given node.
		 * When null is returned, any resolvers for super classes will also be invoked.
		 *
		 * @return  The CSS class name or {@code null} when none configured for the provided node.
		 */
		String getListItemCssClass(N node);
	}

	/**
	 * The CSS classes used in list items.
	 */
	private final Map<Class<? extends com.semanticcms.core.model.Node>, ListItemCssClassResolver<?>> listItemCssClassResolverByNodeType = new LinkedHashMap<>();

	/**
	 * Gets the CSS class to use in list items to the given node.
	 * Also looks for match on parent classes up to and including Node itself.
	 *
	 * @return  The CSS class or {@code null} when node is null or no class registered for it or any super class.
	 *
	 * @see  ListItemCssClassResolver#getListItemCssClass(com.semanticcms.core.model.Node)
	 */
	public <N extends com.semanticcms.core.model.Node> String getListItemCssClass(N node) {
		if(node == null) return null;
		Class<? extends com.semanticcms.core.model.Node> nodeType = node.getClass();
		synchronized(listItemCssClassResolverByNodeType) {
			while(true) {
				@SuppressWarnings("unchecked")
				ListItemCssClassResolver<? super N> listItemCssClassResolver = (ListItemCssClassResolver<? super N>)listItemCssClassResolverByNodeType.get(nodeType);
				if(listItemCssClassResolver != null) {
					String listItemCssClass = listItemCssClassResolver.getListItemCssClass(node);
					if(listItemCssClass != null) return listItemCssClass;
				}
				if(nodeType == com.semanticcms.core.model.Node.class) return null;
				nodeType = nodeType.getSuperclass().asSubclass(com.semanticcms.core.model.Node.class);
			}
		}
	}

	/**
	 * Registers a new CSS resolver to use in list items to the given type of node.
	 *
	 * @throws  IllegalStateException  if the node type is already registered.
	 */
	public <N extends com.semanticcms.core.model.Node> void addListItemCssClassResolver(
		Class<N> nodeType,
		ListItemCssClassResolver<? super N> listItemCssClassResolver
	) throws IllegalStateException {
		synchronized(listItemCssClassResolverByNodeType) {
			if(listItemCssClassResolverByNodeType.containsKey(nodeType)) throw new IllegalStateException("List item CSS class already registered: " + nodeType);
			if(listItemCssClassResolverByNodeType.put(nodeType, listItemCssClassResolver) != null) throw new AssertionError();
		}
	}

	/**
	 * Registers a new CSS class to use in list items to the given type of node.
	 *
	 * @throws  IllegalStateException  if the node type is already registered.
	 */
	// TODO: Take a set of Group.Name activations, too
	public <N extends com.semanticcms.core.model.Node> void addListItemCssClass(
		Class<N> nodeType,
		final String listItemCssClass
	) throws IllegalStateException {
		addListItemCssClassResolver(
			nodeType,
			node -> listItemCssClass
		);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Renderer">

	@Override
	public CaptureLevel getCaptureLevel() {
		// TODO: Capture based on a per-view setting
		return CaptureLevel.PAGE;
	}

	@Override
	public ServletPageRenderer newPageRenderer(Page page, Map<String, ? extends Object> attributes) {
		return new DefaultServletPageRenderer(page, attributes) {

			@Override
			public long getLastModified() throws IOException {
				// TODO: Per-view last modified
				return 0;
			}

			@Override
			public String getContentType() throws IOException {
				// TODO: Per-view content type
				return MediaType.XHTML.getContentType();
			}

			@Override
			public long getLength() throws IOException {
				// TODO: Per-view length
				return -1;
			}

			@Override
			public void doRenderer(
				Page page,
				HttpServletRequest request,
				HttpServletResponse response,
				Writer out // TODO: Pass "out" to theme.doTheme()?
			) throws IOException, ServletException, SkipPageException {
				// Resolve the view
				HtmlRenderer htmlRenderer = HtmlRenderer.getInstance(servletContext);
				View view;
				{
					String viewName = request.getParameter(VIEW_PARAM);
					Map<String, View> viewsByName = htmlRenderer.getViewsByName();
					if(viewName == null) {
						view = null;
					} else {
						if(Link.DEFAULT_VIEW_NAME.equals(viewName)) throw new ServletException(VIEW_PARAM + " paramater may not be sent for default view: " + viewName);
						view = viewsByName.get(viewName);
					}
					if(view == null) {
						// Find default
						view = viewsByName.get(Link.DEFAULT_VIEW_NAME);
						if(view == null) throw new ServletException("Default view not found: " + Link.DEFAULT_VIEW_NAME);
					}
				}

				// Find the theme
				Theme theme = null;
				{
					// Currently just picks the first non-default theme registered, the uses default
					Theme defaultTheme = null;
					for(Theme t : htmlRenderer.getThemes().values()) {
						if(t.isDefault()) {
							assert defaultTheme == null : "More than one default theme registered";
							defaultTheme = t;
						} else {
							// Use first non-default
							theme = t;
							break;
						}
					}
					if(theme == null) {
						// Use default
						if(defaultTheme == null) throw new ServletException("No themes registered");
						theme = defaultTheme;
					}
					assert theme != null;
				}

				// Clear the output buffer
				response.resetBuffer();

				// Set the content type
				// TODO: Pass doctype and serialization through Page object itself.
				// TODO: Split-out non-servlet parts of Html class into a new ao-html project first, and have it be a dependency of core-model.
				// TODO:     We don't want the model picking-up any servlet-specific things, since we may want other non-servlet environments, too, like Play Framework
				// TODO: ServletUtil.setContentType(response, serialization.getContentType(), Html.ENCODING.name());

				Theme oldTheme = Theme.getTheme(request);
				try {
					Theme.setTheme(request, theme);

					// Configure the theme resources
					theme.configureResources(
						servletContext,
						request,
						response,
						view,
						page,
						RegistryEE.Request.get(servletContext, request)
					);

					// Configure the view resources
					view.configureResources(
						servletContext,
						request,
						response,
						theme,
						page,
						RegistryEE.Request.get(servletContext, request)
					);

					// TODO: Configure the page resources here or within view?

					// Forward to theme
					theme.doTheme(servletContext, request, response, view, page);
				} finally {
					Theme.setTheme(request, oldTheme);
				}
			}

			@Override
			public void close() {
				// Do nothing
			}
		};
	}
	// </editor-fold>
}
