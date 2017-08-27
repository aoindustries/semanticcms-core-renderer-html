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

/**
 * The set of allowed component locations.
 */
public enum ComponentPosition {

	/**
	 * Components called just after the head opening tag.
	 * These components are called in the order registered.
	 */
	HEAD_START,

	/**
	 * Components called just before the head closing tag.
	 * These components are called in the reverse order registered.
	 */
	HEAD_END,

	/**
	 * Components called just after the body opening tag.
	 * These components are called in the order registered.
	 */
	BODY_START,

	/**
	 * Components called just before the body closing tag.
	 * These components are called in the reverse order registered.
	 */
	BODY_END
}
