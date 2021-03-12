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

import com.aoindustries.html.A;
import com.aoindustries.html.AnyDocument;
import com.aoindustries.html.LI_c;
import com.aoindustries.html.PalpableContent;
import com.aoindustries.html.SPAN;
import com.aoindustries.html.UL_c;
import com.aoindustries.net.URIEncoder;
import com.semanticcms.core.controller.CapturePage;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.pages.local.CurrentCaptureLevel;
import com.semanticcms.core.pages.local.CurrentNode;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

/**
 * Builds a tree, filtering for a specific element type.
 */
final public class ElementFilterTree {

	/**
	 * A filter to select elements by arbitrary conditions.
	 */
	@FunctionalInterface
	public static interface ElementFilter {

		/**
		 * Checks if matches.
		 */
		boolean matches(Element e);
	}

	/**
	 * A filter to select non-hidden and by element class.
	 */
	public static class ClassFilter implements ElementFilter {

		private final Class<? extends Element> elementType;

		public ClassFilter(Class<? extends Element> elementType) {
			this.elementType = elementType;
		}

		@Override
		public boolean matches(Element e) {
			return !e.isHidden() && elementType.isInstance(e);			
		}
	}

	private static boolean findElements(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		SemanticCMS semanticCMS,
		ElementFilter elementFilter,
		Set<Node> nodesWithMatches,
		Node node,
		boolean includeElements
	) throws ServletException, IOException {
		List<Element> childElements = node.getChildElements();
		boolean hasMatch;
		// Add self if is the target type
		if((node instanceof Element) && elementFilter.matches((Element)node)) {
			hasMatch = true;
		} else {
			hasMatch = false;
			for(Element childElem : childElements) {
				if(elementFilter.matches(childElem)) {
					hasMatch = true;
					break;
				}
			}
		}
		if(includeElements) {
			for(Element childElem : childElements) {
				if(findElements(servletContext, request, response, semanticCMS, elementFilter, nodesWithMatches, childElem, includeElements)) {
					hasMatch = true;
				}
			}
		} else {
			assert (node instanceof Page);
			if(!hasMatch) {
				// Not including elements, so any match from an element must be considered a match from the page the element is on
				Page page = (Page)node;
				for(Element e : page.getElements()) {
					if(elementFilter.matches(e)) {
						hasMatch = true;
						break;
					}
				}
			}
		}
		if(node instanceof Page) {
			for(ChildRef childRef : ((Page)node).getChildRefs()) {
				PageRef childPageRef = childRef.getPageRef();
				// Child is in an accessible book
				if(semanticCMS.getBook(childPageRef.getBookRef()).isAccessible()) {
					Page child = CapturePage.capturePage(servletContext, request, response, childPageRef, CaptureLevel.META);
					if(findElements(servletContext, request, response, semanticCMS, elementFilter, nodesWithMatches, child, includeElements)) {
						hasMatch = true;
					}
				}
			}
		}
		if(hasMatch) {
			nodesWithMatches.add(node);
		}
		return hasMatch;
	}

	/**
	 * @param  <D>   This document type
	 * @param  <__>  {@link Union_Palpable_Phrasing} provides both {@link A} and {@link SPAN}.
	 */
	private static <
		D extends AnyDocument<D>,
		__ extends PalpableContent<D, __>
	> void writeNode(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Node currentNode,
		Set<Node> nodesWithMatches,
		PageIndex pageIndex,
		UL_c<D, __> ul__,
		Node node,
		boolean includeElements
	) throws ServletException, IOException, SkipPageException {
		final Page page;
		final Element element;
		if(node instanceof Page) {
			page = (Page)node;
			element = null;
		} else if(node instanceof Element) {
			assert includeElements;
			element = (Element)node;
			page = element.getPage();
		} else {
			throw new AssertionError();
		}
		final PageRef pageRef = page.getPageRef();
		if(currentNode != null) {
			// Add page links
			currentNode.addPageLink(pageRef);
		}
		LI_c<D, UL_c<D, __>> li_c;
		if(ul__ != null) {
			StringBuilder url = new StringBuilder();
			Integer index = pageIndex==null ? null : pageIndex.getPageIndex(pageRef);
			if(index != null) {
				url.append('#');
				URIEncoder.encodeURIComponent(
					PageIndex.getRefId(
						index,
						element==null ? null : element.getId()
					),
					url
				);
			} else {
				URIEncoder.encodeURI(request.getContextPath(), url);
				URIEncoder.encodeURI(pageRef.getBookRef().getPrefix(), url);
				URIEncoder.encodeURI(pageRef.getPath().toString(), url);
				if(element != null) {
					String elemId = element.getId();
					assert elemId != null;
					url.append('#');
					URIEncoder.encodeURIComponent(elemId, url);
				}
			}
			li_c = ul__.li()
				.clazz(HtmlRenderer.getInstance(servletContext).getListItemCssClass(node))
				._c();
			li_c.a(response.encodeURL(url.toString())).__(a -> {
				a.text(node);
				if(index != null) {
					a.sup__(sup -> sup
						.text('[').text(index + 1).text(']')
					);
				}
			});
		} else {
			li_c = null;
		}
		List<Node> childNodes = NavigationTreeRenderer.getChildNodes(servletContext, request, response, includeElements, true, node);
		childNodes = NavigationTreeRenderer.filterNodes(childNodes, nodesWithMatches);
		if(!childNodes.isEmpty()) {
			UL_c<D, LI_c<D, UL_c<D, __>>> ul_c = (li_c != null) ? li_c.ul_c() : null;
			for(Node childNode : childNodes) {
				writeNode(servletContext, request, response, currentNode, nodesWithMatches, pageIndex, ul_c, childNode, includeElements);
			}
			if(ul_c != null) ul_c.__();
		}
		if(li_c != null) li_c.__();
	}

	// Traversal-based implementation is proving too complicated due to needing to
	// look ahead to know which elements to show.
	// TODO: Caching?
	public static <
		D extends AnyDocument<D>,
		__ extends PalpableContent<D, __>
	> void writeElementFilterTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ content,
		ElementFilter elementFilter,
		Node root,
		boolean includeElements
	) throws ServletException, IOException, SkipPageException {
		// Get the current capture state
		final CaptureLevel captureLevel = CurrentCaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			final Node currentNode = CurrentNode.getCurrentNode(request);
			// Filter by has files
			final Set<Node> nodesWithMatches = new HashSet<>();
			findElements(
				servletContext,
				request,
				response,
				SemanticCMS.getInstance(servletContext),
				elementFilter,
				nodesWithMatches,
				root,
				includeElements
			);
			UL_c<D, __> ul_c = (captureLevel == CaptureLevel.BODY) ? content.ul_c() : null;
			writeNode(
				servletContext,
				request,
				response,
				currentNode,
				nodesWithMatches,
				PageIndex.getCurrentPageIndex(request),
				ul_c,
				root,
				includeElements
			);
			if(ul_c != null) ul_c.__();
		}
	}

	public static <
		D extends AnyDocument<D>,
		__ extends PalpableContent<D, __>
	> void writeElementFilterTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ content,
		Class<? extends Element> elementType,
		Node root,
		boolean includeElements
	) throws ServletException, IOException, SkipPageException {
		writeElementFilterTreeImpl(
			servletContext,
			request,
			response,
			content,
			new ClassFilter(elementType),
			root,
			includeElements
		);
	}

	/**
	 * Make no instances.
	 */
	private ElementFilterTree() {
	}
}
