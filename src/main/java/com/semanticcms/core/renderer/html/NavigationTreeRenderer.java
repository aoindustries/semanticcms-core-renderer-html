/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019  AO Industries, Inc.
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

import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.textInXhtmlAttributeEncoder;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoindustries.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoindustries.net.DomainName;
import com.aoindustries.net.Path;
import static com.aoindustries.taglib.AttributeUtils.resolveValue;
import com.aoindustries.util.StringUtility;
import static com.aoindustries.util.StringUtility.nullIfEmpty;
import com.aoindustries.validation.ValidationException;
import com.semanticcms.core.controller.CapturePage;
import com.semanticcms.core.controller.PageRefResolver;
import com.semanticcms.core.controller.PageUtils;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.PageReferrer;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.pages.local.CurrentCaptureLevel;
import com.semanticcms.core.pages.local.CurrentNode;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// TODO: Should have a new navigation-tree-* set of packages for this and related model, servlet, taglib
final public class NavigationTreeRenderer {

	public static <T extends Node> List<T> filterNodes(Collection<T> children, Set<T> nodesToInclude) {
		int size = children.size();
		if(size == 0) return Collections.emptyList();
		List<T> filtered = new ArrayList<>(size);
		for(T child : children) {
			if(nodesToInclude.contains(child)) {
				filtered.add(child);
			}
		}
		return filtered;
	}

	public static <T extends PageReferrer> List<T> filterPages(Collection<T> children, Set<PageRef> pagesToInclude) {
		int size = children.size();
		if(size == 0) return Collections.emptyList();
		List<T> filtered = new ArrayList<>(size);
		for(T child : children) {
			if(pagesToInclude.contains(child.getPageRef())) {
				filtered.add(child);
			}
		}
		return filtered;
	}

	public static List<Node> getChildNodes(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		boolean includeElements,
		boolean metaCapture,
		Node node
	) throws ServletException, IOException {
		// Both elements and pages are child nodes
		List<Element> childElements = includeElements ? node.getChildElements() : null;
		Set<ChildRef> childRefs = (node instanceof Page) ? ((Page)node).getChildRefs() : null;
		List<Node> childNodes = new ArrayList<>(
			(childElements==null ? 0 : childElements.size())
			+ (childRefs==null ? 0 : childRefs.size())
		);
		if(includeElements) {
			for(Element childElem : childElements) {
				if(!childElem.isHidden()) childNodes.add(childElem);
			}
		}
		if(childRefs != null) {
			SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			for(ChildRef childRef : childRefs) {
				PageRef childPageRef = childRef.getPageRef();
				// Child is in an accessible book
				if(semanticCMS.getBook(childPageRef.getBookRef()).isAccessible()) {
					Page childPage = CapturePage.capturePage(servletContext, request, response, childPageRef, includeElements || metaCapture ? CaptureLevel.META : CaptureLevel.PAGE);
					childNodes.add(childPage);
				}
			}
		}
		return childNodes;
	}

	private static boolean findLinks(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		SemanticCMS semanticCMS,
		PageRef linksTo,
		Set<Node> nodesWithLinks,
		Set<Node> nodesWithChildLinks,
		Node node,
		boolean includeElements
	) throws ServletException, IOException {
		boolean hasChildLink = false;
		if(node.getPageLinks().contains(linksTo)) {
			nodesWithLinks.add(node);
			hasChildLink = true;
		}
		if(includeElements) {
			for(Element childElem : node.getChildElements()) {
				if(
					!childElem.isHidden()
					&& findLinks(servletContext, request, response, semanticCMS, linksTo, nodesWithLinks, nodesWithChildLinks, childElem, includeElements)
				) {
					hasChildLink = true;
				}
			}
		} else {
			assert (node instanceof Page);
			if(!hasChildLink) {
				// Not including elements, so any link from an element must be considered a link from the page the element is on
				Page page = (Page)node;
				for(Element e : page.getElements()) {
					if(e.getPageLinks().contains(linksTo)) {
						nodesWithLinks.add(node);
						hasChildLink = true;
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
					if(findLinks(servletContext, request, response, semanticCMS, linksTo, nodesWithLinks, nodesWithChildLinks, child, includeElements)) {
						hasChildLink = true;
					}
				}
			}
		}
		if(hasChildLink) {
			nodesWithChildLinks.add(node);
		}
		return hasChildLink;
	}

	public static String encodeHexData(String data) {
		// Note: This is always UTF-8 encoded and does not depend on response encoding
		return StringUtility.convertToHex(data.getBytes(StandardCharsets.UTF_8));
	}

	public static void writeNavigationTree(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		Page root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		// TODO: PageRef
		DomainName thisDomain,
		Path thisBook,
		String thisPage,
		// TODO: PageRef
		DomainName linksToDomain,
		Path linksToBook,
		String linksToPage,
		int maxDepth
	) throws ServletException, IOException {
		// Get the current capture state
		CaptureLevel captureLevel = CurrentCaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			writeNavigationTreeImpl(
				servletContext,
				request,
				response,
				out,
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
				maxDepth,
				captureLevel
			);
		}
	}

	/**
	 * @param root           ValueExpression that returns Page
	 * @param thisDomain     ValueExpression that returns String
	 * @param thisBook       ValueExpression that returns String
	 * @param thisPage       ValueExpression that returns String
	 * @param linksToDomain  ValueExpression that returns String
	 * @param linksToBook    ValueExpression that returns String
	 * @param linksToPage    ValueExpression that returns String
	 */
	public static void writeNavigationTree(
		ServletContext servletContext,
		ELContext elContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		ValueExpression root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		ValueExpression thisDomain,
		ValueExpression thisBook,
		ValueExpression thisPage,
		ValueExpression linksToDomain,
		ValueExpression linksToBook,
		ValueExpression linksToPage,
		int maxDepth
	) throws ServletException, IOException {
		// Get the current capture state
		CaptureLevel captureLevel = CurrentCaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			try {
				writeNavigationTreeImpl(
					servletContext,
					request,
					response,
					out,
					resolveValue(root, Page.class, elContext),
					skipRoot,
					yuiConfig,
					includeElements,
					target,
					DomainName.valueOf(
						nullIfEmpty(
							resolveValue(thisDomain, String.class, elContext)
						)
					),
					Path.valueOf(
						StringUtility.nullIfEmpty(
							resolveValue(thisBook, String.class, elContext)
						)
					),
					resolveValue(thisPage,   String.class, elContext),
					DomainName.valueOf(
						StringUtility.nullIfEmpty(
							resolveValue(linksToDomain, String.class, elContext)
						)
					),
					Path.valueOf(
						StringUtility.nullIfEmpty(
							resolveValue(linksToBook,   String.class, elContext)
						)
					),
					resolveValue(linksToPage,   String.class, elContext),
					maxDepth,
					captureLevel
				);
			} catch(ValidationException e) {
				throw new ServletException(e);
			}
		}
	}

	private static void writeNavigationTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		Page root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		DomainName thisDomain,
		Path thisBook,
		String thisPage,
		DomainName linksToDomain,
		Path linksToBook,
		String linksToPage,
		int maxDepth,
		CaptureLevel captureLevel
	) throws ServletException, IOException {
		assert captureLevel.compareTo(CaptureLevel.META) >= 0;
		final Node currentNode = CurrentNode.getCurrentNode(request);

		thisPage = nullIfEmpty(thisPage);
		if(thisDomain != null && thisBook == null) {
			throw new ServletException("thisBook must be provided when thisDomain is provided.");
		}
		linksToPage = nullIfEmpty(linksToPage);
		if(linksToDomain != null && linksToBook == null) {
			throw new ServletException("linksToBook must be provided when linksToDomain is provided.");
		}

		// Filter by link-to
		final Set<Node> nodesWithLinks;
		final Set<Node> nodesWithChildLinks;
		if(linksToPage == null) {
			if(linksToBook != null) throw new ServletException("linksToPage must be provided when linksToBook is provided.");
			nodesWithLinks = null;
			nodesWithChildLinks = null;
		} else {
			// Find all nodes in the navigation tree that link to the linksToPage
			PageRef linksTo = PageRefResolver.getPageRef(servletContext, request, linksToDomain, linksToBook, linksToPage);
			nodesWithLinks = new HashSet<>();
			nodesWithChildLinks = new HashSet<>();
			findLinks(
				servletContext,
				request,
				response,
				SemanticCMS.getInstance(servletContext),
				linksTo,
				nodesWithLinks,
				nodesWithChildLinks,
				root,
				includeElements
			);
		}

		PageRef thisPageRef;
		if(thisPage == null) {
			if(thisBook != null) throw new ServletException("thisPage must be provided when thisBook is provided.");
			thisPageRef = null;
		} else {
			thisPageRef = PageRefResolver.getPageRef(servletContext, request, thisDomain, thisBook, thisPage);
		}

		boolean foundThisPage = false;
		PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
		if(skipRoot) {
			List<Node> childNodes = NavigationTreeRenderer.getChildNodes(
				servletContext,
				request,
				response,
				includeElements,
				false,
				root
			);
			if(nodesWithChildLinks != null) {
				childNodes = NavigationTreeRenderer.filterNodes(childNodes, nodesWithChildLinks);
			}
			if(!childNodes.isEmpty()) {
				if(captureLevel == CaptureLevel.BODY) out.write("<ul>\n");
				for(Node childNode : childNodes) {
					foundThisPage = writeNode(
						servletContext,
						request,
						response,
						captureLevel == CaptureLevel.BODY ? out : null,
						currentNode,
						nodesWithLinks,
						nodesWithChildLinks,
						pageIndex,
						null, // parentPageRef
						childNode,
						yuiConfig,
						includeElements,
						target,
						thisPageRef,
						foundThisPage,
						maxDepth,
						1
					);
				}
				if(captureLevel == CaptureLevel.BODY) out.write("</ul>\n");
			}
		} else {
			if(captureLevel == CaptureLevel.BODY) out.write("<ul>\n");
			/*foundThisPage =*/ writeNode(
				servletContext,
				request,
				response,
				captureLevel == CaptureLevel.BODY ? out : null,
				currentNode,
				nodesWithLinks,
				nodesWithChildLinks,
				pageIndex,
				null,
				root,
				yuiConfig,
				includeElements,
				target,
				thisPageRef,
				foundThisPage,
				maxDepth,
				1
			);
			if(captureLevel == CaptureLevel.BODY) out.write("</ul>\n");
		}
	}

	private static boolean writeNode(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		Node currentNode,
		Set<Node> nodesWithLinks,
		Set<Node> nodesWithChildLinks,
		PageIndex pageIndex,
		PageRef parentPageRef,
		Node node,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		PageRef thisPageRef,
		boolean foundThisPage,
		int maxDepth,
		int level
	) throws IOException, ServletException {
		final Page page;
		final Element element;
		if(node instanceof Page) {
			page = (Page)node;
			element = null;
		} else if(node instanceof Element) {
			assert includeElements;
			element = (Element)node;
			assert !element.isHidden();
			page = element.getPage();
		} else {
			throw new AssertionError();
		}
		final PageRef pageRef = page.getPageRef();
		if(currentNode != null) {
			// Add page links
			currentNode.addPageLink(pageRef);
		}
		final String servletPath;
		if(out == null) {
			// Will be unused
			servletPath = null;
		} else {
			if(element == null) {
				servletPath = pageRef.getBookRef().getPrefix() + pageRef.getPath();
			} else {
				String elemId = element.getId();
				assert elemId != null;
				servletPath = pageRef.getBookRef().getPrefix() + pageRef.getPath() + '#' + elemId;
			}
		}
		if(out != null) {
			out.write("<li");
			if(yuiConfig) {
				out.write(" yuiConfig='{\"data\":\"");
				encodeTextInXhtmlAttribute(encodeHexData(servletPath), out);
				out.write("\"}'");
			}
			String listItemCssClass = HtmlRenderer.getInstance(servletContext).getListItemCssClass(node);
			if(listItemCssClass != null || level == 1) {
				out.write(" class=\"");
				boolean didClass = false;
				if(listItemCssClass != null) {
					encodeTextInXhtmlAttribute(listItemCssClass, out);
					didClass = true;
				}
				if(level == 1) {
					if(didClass) out.write(' ');
					out.write("expanded");
				}
				out.write('"');
			}
			out.write("><a");
		}
		// Look for thisPage match
		boolean thisPageClass = false;
		if(pageRef.equals(thisPageRef) && element == null) {
			if(!foundThisPage) {
				if(out != null) out.write(" id=\"semanticcms-core-tree-this-page\"");
				foundThisPage = true;
			}
			thisPageClass = true;
		}
		// Look for linkToPage match
		boolean linksToPageClass = nodesWithLinks!=null && nodesWithLinks.contains(node);
		if(out != null && (thisPageClass || linksToPageClass)) {
			out.write(" class=\"");
			if(thisPageClass && nodesWithLinks!=null && !linksToPageClass) {
				out.write("semanticcms-core-no-link-to-this-page");
			} else if(thisPageClass) {
				out.write("semanticcms-core-tree-this-page");
			} else if(linksToPageClass) {
				out.write("semanticcms-core-links-to-page");
			} else {
				throw new AssertionError();
			}
			out.write('"');
		}
		if(out != null) {
			if(target != null) {
				out.write(" target=\"");
				encodeTextInXhtmlAttribute(target, out);
				out.write('"');
			}
			out.write(" href=\"");
			Integer index = pageIndex==null ? null : pageIndex.getPageIndex(pageRef);
			if(index != null) {
				out.write('#');
				PageIndex.appendIdInPage(
					index,
					element==null ? null : element.getId(),
					new MediaWriter(textInXhtmlAttributeEncoder, out)
				);
			} else {
				encodeTextInXhtmlAttribute(
					response.encodeURL(
						com.aoindustries.net.UrlUtils.encodeUrlPath(
							request.getContextPath() + servletPath,
							response.getCharacterEncoding()
						)
					),
					out
				);
			}
			out.write("\">");
			if(node instanceof Page) {
				// Use shortTitle for pages
				encodeTextInXhtml(
					PageUtils.getShortTitle(parentPageRef, (Page)node),
					out
				);
			} else {
				node.appendLabel(new MediaWriter(textInXhtmlEncoder, out));
			}
			if(index != null) {
				out.write("<sup>[");
				encodeTextInXhtml(Integer.toString(index+1), out);
				out.write("]</sup>");
			}
			out.write("</a>");
		}
		if(maxDepth==0 || level < maxDepth) {
			List<Node> childNodes = NavigationTreeRenderer.getChildNodes(servletContext, request, response, includeElements, false, node);
			if(nodesWithChildLinks!=null) {
				childNodes = NavigationTreeRenderer.filterNodes(childNodes, nodesWithChildLinks);
			}
			if(!childNodes.isEmpty()) {
				if(out != null) {
					out.write('\n');
					out.write("<ul>\n");
				}
				for(Node childNode : childNodes) {
					foundThisPage = writeNode(
						servletContext,
						request,
						response,
						out,
						currentNode,
						nodesWithLinks,
						nodesWithChildLinks,
						pageIndex,
						element==null ? pageRef : parentPageRef,
						childNode,
						yuiConfig,
						includeElements,
						target,
						thisPageRef,
						foundThisPage,
						maxDepth,
						level+1
					);
				}
				if(out != null) out.write("</ul>\n");
			}
		}
		if(out != null) out.write("</li>\n");
		return foundThisPage;
	}

	/**
	 * Make no instances.
	 */
	private NavigationTreeRenderer() {
	}
}
