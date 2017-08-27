/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2016, 2017  AO Industries, Inc.
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

import com.semanticcms.core.model.Page;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utilities for working with components.
 */
final public class ComponentUtils {

	public static void doComponents(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		View view,
		Page page,
		ComponentPosition position,
		boolean reverse
	) throws ServletException, IOException {
		List<Component> components = HtmlRenderer.getInstance(servletContext).getComponents();
		if(reverse) {
			for(int i=components.size()-1; i>=0; i--) {
				components.get(i).doComponent(
					servletContext,
					request,
					response,
					out,
					view,
					page,
					position
				);
			}
		} else {
			for(Component component : components) {
				component.doComponent(
					servletContext,
					request,
					response,
					out,
					view,
					page,
					position
				);
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private ComponentUtils() {
	}
}
