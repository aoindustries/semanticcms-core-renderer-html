/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2016, 2017, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoindustries.html.servlet.FlowContent;
import com.aoindustries.net.URIEncoder;
import com.aoindustries.servlet.http.Canonical;
import com.aoindustries.web.resources.registry.Registry;
import com.semanticcms.core.controller.AuthorUtils;
import com.semanticcms.core.controller.Book;
import com.semanticcms.core.controller.BookUtils;
import com.semanticcms.core.controller.CopyrightUtils;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.Author;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Copyright;
import com.semanticcms.core.model.Link;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;
import org.joda.time.ReadableInstant;

/**
 * A site may provide multiple views of the data.  Except the default content view,
 * views will typically show something about the current page and all of its children.
 */
abstract public class View implements Comparable<View> {

	/**
	 * The separator used between segments of the title.
	 * Should this be provided by the template?
	 */
	protected static final String TITLE_SEPARATOR = " - ";

	/**
	 * View groupings, in order.
	 */
	public enum Group {
		/**
		 * Things that should be placed absolutely first.
		 */
		FIRST,

		/**
		 * The first set of views are those that are more fixed - typically displayed on every page.
		 */
		FIXED,

		/**
		 * The second set of views are those that are hidden when not relevant to the current page or any of its children.
		 * This often includes the per-element-type views.
		 */
		VARIABLE
	}

	/**
	 * Orders by group, display, then name.
	 */
	@Override
	public int compareTo(View o) {
		int diff = getGroup().compareTo(o.getGroup());
		if(diff != 0) return diff;
		diff = getDisplay().compareTo(o.getDisplay());
		if(diff != 0) return diff;
		diff = getName().compareTo(o.getName());
		return diff;
	}

	/**
	 * Two views with the same name are considered equal.
	 */
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof View)) return false;
		View o = (View)obj;
		return getName().equals(o.getName());
	}

	/**
	 * Consistent with equals, hashCode based on name.
	 */
	@Override
	public int hashCode() {
		return getName().hashCode();
	}

	/**
	 * @see  #getDisplay()
	 */
	@Override
	public String toString() {
		return getDisplay();
	}

	/**
	 * Gets the grouping for this view.
	 */
	abstract public Group getGroup();

	/**
	 * Gets the display name for this view.
	 */
	abstract public String getDisplay();

	/**
	 * Gets the unique name of this view.
	 */
	abstract public String getName();

	/**
	 * Checks if this is the default view.
	 */
	final public boolean isDefault() {
		return Link.DEFAULT_VIEW_NAME.equals(getName());
	}

	/**
	 * Checks if a view applies in global navigation context.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * returns {@code true} by default
	 * </p>
	 */
	public boolean getAppliesGlobally() {
		return true;
	}

	/**
	 * Checks if a view is applicable the given request and page.
	 * For correct determination, the page must have been captured at {@link CaptureLevel#META}
	 * level or higher.
	 * <p>
	 * TODO: Store the captureLevel in effect when a page is captured, and confirm that here and other places where
	 *       certain capture levels are required for correct behavior.  Could also automatically re-capture at a higher level
	 *       instead of throwing an exception.
	 * </p>
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * returns {@code true} by default
	 * </p>
	 */
	public boolean isApplicable(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return true;
	}

	/**
	 * Gets an id to use for the main navigation link to this view.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * returns {@code null} by default
	 * </p>
	 *
	 * @return  the ID or null for none
	 */
	public String getLinkId() {
		return null;
	}

	/**
	 * Gets the CSS class to use for the main navigation link to this view.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * returns {@code null} by default
	 * </p>
	 *
	 * @return  the CSS class or null for none
	 */
	public String getLinkCssClass(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		return null;
	}

	/**
	 * Gets the optional additional parameter to a view link.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * returns empty map by default
	 * </p>
	 */
	public Map<String, List<String>> getLinkParams(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, Page page) {
		return Collections.emptyMap();
	}

	/**
	 * Gets the canonical URL for the given page in this view.
	 * This might be called even when a page is not applicable to this view, such as when browsing to an empty TO-DO list.
	 * By default, {@link #getLinkParams(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page) link parameters}
	 * are not added.
	 * <p>
	 * This URL is absolute and has already been response encoded.
	 * </p>
	 * @see  Book#getCanonicalBase()
	 * @see  BookUtils#getCanonicalBase(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, com.semanticcms.core.controller.Book)
	 */
	public String getCanonicalUrl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		PageRef pageRef = page.getPageRef();
		BookRef bookRef = pageRef.getBookRef();
		// TODO: Should we use servletPath here, then remove the book prefix?
		//       We were passing a partial path to response.encodeURL
		String encodedBookPrefix = URIEncoder.encodeURI(bookRef.getPrefix());
		String encodedServletPath;
		{
			StringBuilder servletPath = new StringBuilder();
			servletPath.append(encodedBookPrefix);
			URIEncoder.encodeURI(pageRef.getPath().toString(), servletPath);
			if(!isDefault()) {
				servletPath.append("?view=");
				URIEncoder.encodeURIComponent(getName(), servletPath);
			}
			encodedServletPath = Canonical.encodeCanonicalURL(response, servletPath.toString());
		}
		// To be safe, we're encoding the servletPath, then picking it back into a bookPath
		// TODO: How would this interact with things like PrettyUrlFilter?
		String encodedBookPath;
		{
			if(encodedBookPrefix.isEmpty()) {
				encodedBookPath = encodedServletPath;
			} else {
				if(!encodedServletPath.startsWith(encodedBookPrefix)) throw new IllegalStateException("Encoded servlet path is outside this book, unable to canonicalize: encodedServletPath = " + encodedServletPath);
				encodedBookPath = encodedServletPath.substring(encodedBookPrefix.length());
			}
		}
		return BookUtils.getCanonicalBase(
			servletContext,
			request,
			SemanticCMS.getInstance(servletContext).getBook(bookRef)
		) + encodedBookPath;
	}

	/**
	 * Gets the effective last modified time, if known, for the given page in this view.
	 * This is used for things such as sitemaps.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation returns {@code null} indicating not applicable to this view.
	 * </p>
	 *
	 * @return  The effective last modified time or {@code null} if unknown or not applicable.
	 */
	public ReadableInstant getLastModified(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return null;
	}

	/**
	 * Gets the copyright information for the view on the given page.
	 * 
	 * @see  CopyrightUtils#findCopyright(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page)
	 */
	public Copyright getCopyright(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return CopyrightUtils.findCopyright(servletContext, request, response, page);
	}

	/**
	 * Gets the author(s) for the view on the given page.
	 *
	 * @see  AuthorUtils#findAuthors(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page)
	 */
	public Set<Author> getAuthors(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return AuthorUtils.findAuthors(servletContext, request, response, page);
	}

	/**
	 * Gets the page title for the view on the given page.
	 *
	 * Defaults to: "view.display - page.title[ - page.pageRef.book.title]"
	 */
	public String getTitle(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) {
		String bookTitle = SemanticCMS.getInstance(servletContext).getBook(page.getPageRef().getBookRef()).getTitle();
		if(bookTitle != null && !bookTitle.isEmpty()) {
			return getDisplay() + TITLE_SEPARATOR + page.getTitle() + TITLE_SEPARATOR + bookTitle;
		} else {
			return getDisplay() + TITLE_SEPARATOR + page.getTitle();
		}
	}

	/**
	 * Gets the description for this view of the given page or {@code null} for none.
	 */
	abstract public String getDescription(Page page);

	/**
	 * Gets the keywords for this view of the given page or {@code null} for none.
	 */
	abstract public String getKeywords(Page page);

	/**
	 * Configures the {@linkplain com.aoindustries.web.resources.servlet.RegistryEE.Request request-scope web resources} that this view uses.
	 * <p>
	 * Implementers should call <code>super.configureResources(…)</code> as a matter of convention, despite this default implementation doing nothing.
	 * </p>
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	public void configureResources(ServletContext servletContext, HttpServletRequest req, HttpServletResponse resp, Theme theme, Page page, Registry requestRegistry) {
		// Do nothing
	}

	/**
	 * Gets an optional set of additional links to include for this view
	 * in the order they should be added.
	 * <p>
	 * Please note, that any links to stylesheets here are never optimized.  Please
	 * prefer {@link #configureResources(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.renderer.html.Theme, com.semanticcms.core.model.Page, com.aoindustries.web.resources.registry.Registry)}.
	 * </p>
	 *
	 * @see  #configureResources(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.renderer.html.Theme, com.semanticcms.core.model.Page, com.aoindustries.web.resources.registry.Registry)
	 */
	public Collection<com.aoindustries.taglib.Link> getLinks(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		// TODO: Theme here, too?
		Page page
	) throws ServletException, IOException {
		return Collections.emptySet();
	}

	/**
	 * Gets any per-view scripts, when have the same name as globally registered
	 * scripts, must have matching src.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * returns empty map by default
	 * </p>
	 *
	 * @see  HtmlRenderer#getScripts()
	 */
	public Map<String, String> getScripts() {
		return Collections.emptyMap();
	}

	/**
	 * Gets whether robots are allowed to access this view to the given page.  When true will include both
	 * "noindex, nofollow" in the head and put "nofollow" on links to this view.
	 */
	abstract public boolean getAllowRobots(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException;

	/**
	 * Renders the view.  This is called by the template to fill-out the main content area.
	 *
	 * TODO: Is SkipPageException acceptable at the view rendering stage?
	 */
	abstract public <__ extends FlowContent<__>> void doView(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ flow,
		Page page
	) throws ServletException, IOException, SkipPageException;
}
