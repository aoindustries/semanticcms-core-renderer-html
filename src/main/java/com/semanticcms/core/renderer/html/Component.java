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

import com.aoindustries.html.Document;
import com.semanticcms.core.model.Page;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A component is able to render itself within the page.  The theme will
 * call all registered components at the correct stages of page rendering.
 */
@FunctionalInterface
public interface Component {

	/**
	 * <p>
	 * Renders a component in the output stream.
	 * </p>
	 * <p>
	 * This will be called for each of the component positions as the page
	 * is rendered by the theme.  A component that does not apply to the given
	 * view, page, or position should take no action and return quickly.
	 * </p>
	 *
	 * @param view  The view that is currently being rendered.  May be {@code null} during error handling.
	 * @param page  The page that is currently being rendered.  May be {@code null} during error handling.
	 */
	// TODO: Different methods for each position, with default no-ops.
	//       No more "ComponentPosition".
	//       Pass more specific *Content types rather than generic Document
	void doComponent(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Document document,
		View view,
		Page page,
		ComponentPosition position
	) throws ServletException, IOException;
}
