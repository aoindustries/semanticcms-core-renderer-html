/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2015, 2016, 2017  AO Industries, Inc.
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

import javax.servlet.http.HttpServletRequest;

/**
 * Provides static access to the client-provided headers.
 */
public class Headers {

	/**
	 * A client may include this header to indicate it is in export mode.
	 */
	private static final String EXPORTING_HEADER = "X-com-semanticcms-core-renderer-html-exporting";

	/**
	 * The value to pass in the header.
	 */
	private static final String EXPORTING_HEADER_VALUE = "true";

	/**
	 * Checks if the request is for an export.
	 * <p>
	 * TODO: Automatically disable auto-last-modified during export to only require a single header.
	 * </p>
	 */
	public static boolean isExporting(HttpServletRequest request) {
		return EXPORTING_HEADER_VALUE.equalsIgnoreCase(request.getHeader(EXPORTING_HEADER));
	}

	/**
	 * Make no instances.
	 */
	private Headers() {
	}
}
