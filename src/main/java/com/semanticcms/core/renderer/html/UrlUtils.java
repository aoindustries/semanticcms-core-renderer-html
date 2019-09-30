/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019  AO Industries, Inc.
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

import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import com.aoindustries.net.URIParameters;
import com.aoindustries.servlet.http.HttpServletUtil;
import com.aoindustries.servlet.http.LastModifiedServlet;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Helper utility for handling URLs.
 */
final public class UrlUtils {

	/**
	 * Writes an href attribute with parameters.
	 * Adds contextPath to URLs that begin with a slash (/).
	 * Encodes the URL.
	 */
	public static void writeHref(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		String href,
		URIParameters params,
		boolean hrefAbsolute,
		LastModifiedServlet.AddLastModifiedWhen addLastModified
	) throws ServletException, IOException {
		if(href != null) {
			out.append(" href=\"");
			encodeTextInXhtmlAttribute(
				HttpServletUtil.buildUrl(
					servletContext,
					request,
					response,
					href,
					params,
					hrefAbsolute,
					addLastModified
				),
				out
			);
			out.append('"');
		} else {
			if(params != null) throw new ServletException("parameters provided without href");
		}
	}

	public static void writeSrc(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		String src,
		URIParameters params,
		boolean srcAbsolute,
		LastModifiedServlet.AddLastModifiedWhen addLastModified
	) throws ServletException, IOException {
		if(src != null) {
			out.append(" src=\"");
			encodeTextInXhtmlAttribute(
				HttpServletUtil.buildUrl(
					servletContext,
					request,
					response,
					src,
					params,
					srcAbsolute,
					addLastModified
				),
				out
			);
			out.append('"');
		} else {
			if(params != null) throw new ServletException("parameters provided without src");
		}
	}

	/**
	 * Make no instances.
	 */
	private UrlUtils() {
	}
}
