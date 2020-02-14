/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2020  AO Industries, Inc.
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

import com.aoindustries.html.servlet.HtmlEE;
import com.aoindustries.net.DomainName;
import com.aoindustries.net.Path;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.pages.local.PageContext;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

// TODO: This should have a model component for separation of content from rendering
// TODO: This would make the navigation tree fetch pages from the rendering server's point of view
public class NavigationTree {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final Page root;

	private boolean skipRoot;
	private boolean yuiConfig;
	private boolean includeElements;
	private String target;
	private DomainName thisDomain;
	private Path thisBook;
	private String thisPage;
	private DomainName linksToDomain;
	private Path linksToBook;
	private String linksToPage;
	private int maxDepth;

	public NavigationTree(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page root
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
		this.root = root;
	}

	/**
	 * Creates a new navigation tree in the current page context.
	 *
	 * @see  PageContext
	 */
	public NavigationTree(Page root) {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			root
		);
	}

	public NavigationTree skipRoot(boolean skipRoot) {
		this.skipRoot = skipRoot;
		return this;
	}

	public NavigationTree yuiConfig(boolean yuiConfig) {
		this.yuiConfig = yuiConfig;
		return this;
	}

	public NavigationTree includeElements(boolean includeElements) {
		this.includeElements = includeElements;
		return this;
	}

	public NavigationTree target(String target) {
		this.target = target;
		return this;
	}

	public NavigationTree thisDomain(DomainName thisDomain) {
		this.thisDomain = thisDomain;
		return this;
	}

	public NavigationTree thisBook(Path thisBook) {
		this.thisBook = thisBook;
		return this;
	}

	public NavigationTree thisPage(String thisPage) {
		this.thisPage = thisPage;
		return this;
	}

	public NavigationTree linksToDomain(DomainName linksToDomain) {
		this.linksToDomain = linksToDomain;
		return this;
	}

	public NavigationTree linksToBook(Path linksToBook) {
		this.linksToBook = linksToBook;
		return this;
	}

	public NavigationTree linksToPage(String linksToPage) {
		this.linksToPage = linksToPage;
		return this;
	}

	public NavigationTree maxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
		return this;
	}

	public void invoke() throws ServletException, IOException, SkipPageException {
		NavigationTreeRenderer.writeNavigationTree(
			servletContext,
			request,
			response,
			HtmlEE.get(servletContext, request, response.getWriter()),
			root,
			skipRoot,
			yuiConfig,
			includeElements,
			target,
			thisDomain,
			thisBook,
			thisPage,
			linksToDomain,
			linksToBook,
			linksToPage,
			maxDepth
		);
	}
}
