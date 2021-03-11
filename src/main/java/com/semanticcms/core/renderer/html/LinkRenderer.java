/*
 * semanticcms-core-renderer-html - SemanticCMS pages rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021  AO Industries, Inc.
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
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.exception.WrappedException;
import com.aoindustries.html.A;
import com.aoindustries.html.A_factory;
import com.aoindustries.html.AnyDocument;
import com.aoindustries.html.SPAN;
import com.aoindustries.html.SPAN_c;
import com.aoindustries.html.SPAN_factory;
import com.aoindustries.html.Union_Palpable_Phrasing;
import static com.aoindustries.lang.Strings.nullIfEmpty;
import com.aoindustries.net.DomainName;
import com.aoindustries.net.Path;
import com.aoindustries.net.URIEncoder;
import com.aoindustries.net.URIParameters;
import com.aoindustries.servlet.http.HttpServletUtil;
import static com.aoindustries.taglib.AttributeUtils.resolveValue;
import com.aoindustries.validation.ValidationException;
import com.semanticcms.core.controller.Book;
import com.semanticcms.core.controller.CapturePage;
import com.semanticcms.core.controller.PageRefResolver;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Link;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.pages.local.CurrentCaptureLevel;
import com.semanticcms.core.pages.local.CurrentNode;
import com.semanticcms.core.pages.local.CurrentPage;
import java.io.IOException;
import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class LinkRenderer {

	/**
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 */
	@FunctionalInterface
	public static interface LinkRendererBody<Ex extends Throwable> {
		void doBody(boolean discard) throws Ex, IOException, SkipPageException;
	}

	/**
	 * Writes a broken path reference as "¿domain:/book/path{#targetId}?", no encoding.
	 *
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.String, java.lang.Appendable)
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef, java.lang.String)
	 */
	// TODO: Encoder variants
	public static void writeBrokenPath(PageRef pageRef, String targetId, Appendable out) throws IOException {
		BookRef bookRef = pageRef.getBookRef();
		out
			.append('¿')
			.append(bookRef.getDomain().toString())
			.append(':')
			.append(bookRef.getPrefix())
			.append(pageRef.getPath().toString())
		;
		if(targetId != null) {
			out.append('#').append(targetId);
		}
		out.append('?');
	}

	/**
	 * Writes a broken path reference as "¿domain:/book/path?", no encoding.
	 *
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.String, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtmlAttribute(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef)
	 */
	public static void writeBrokenPath(PageRef pageRef, Appendable out) throws IOException {
		writeBrokenPath(pageRef, null, out);
	}

	/**
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef)
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.String, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.String, java.lang.Appendable)
	 */
	public static String getBrokenPath(PageRef pageRef, String targetId) {
		BookRef bookRef = pageRef.getBookRef();
		int sbLen =
			1 // '¿'
			+ bookRef.getDomain().toString().length()
			+ 1 // ':'
			+ bookRef.getPrefix().length()
			+ pageRef.getPath().toString().length();
		if(targetId != null) {
			sbLen +=
				1 // '#'
				+ targetId.length();
		}
		sbLen++; // '?'
		StringBuilder sb = new StringBuilder(sbLen);
		try {
			writeBrokenPath(pageRef, targetId, sb);
		} catch(IOException e) {
			throw new AssertionError("Should not happen on StringBuilder", e);
		}
		assert sb.length() == sbLen;
		return sb.toString();
	}

	/**
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef, java.lang.String)
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtmlAttribute(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 */
	public static String getBrokenPath(PageRef pageRef) {
		return getBrokenPath(pageRef, null);
	}

	/**
	 * Writes a broken path reference as "¿domain:/book/path{#targetId}?", encoding for XHTML.
	 *
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.String, java.lang.Appendable)
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef, java.lang.String)
	 */
	// TODO: Convert to a single Encoder variant
	public static void writeBrokenPathInXhtml(PageRef pageRef, String targetId, Appendable out) throws IOException {
		BookRef bookRef = pageRef.getBookRef();
		out.append('¿');
		encodeTextInXhtml(bookRef.getDomain().toString(), out);
		out.append(':');
		encodeTextInXhtml(bookRef.getPrefix(), out);
		encodeTextInXhtml(pageRef.getPath().toString(), out);
		if(targetId != null) {
			out.append('#');
			encodeTextInXhtml(targetId, out);
		}
		out.append('?');
	}

	/**
	 * Writes a broken path reference as "¿domain:/book/path?", encoding for XHTML.
	 *
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.String, java.lang.Appendable)
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtmlAttribute(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef)
	 */
	public static void writeBrokenPathInXhtml(PageRef pageRef, Appendable out) throws IOException {
		writeBrokenPathInXhtml(pageRef, null, out);
	}

	/**
	 * Writes a broken path reference as "¿domain:/book/path?", encoding for XML attribute.
	 *
	 * @see  #writeBrokenPath(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #writeBrokenPathInXhtml(com.semanticcms.core.model.PageRef, java.lang.Appendable)
	 * @see  #getBrokenPath(com.semanticcms.core.model.PageRef)
	 */
	public static void writeBrokenPathInXhtmlAttribute(PageRef pageRef, Appendable out) throws IOException {
		BookRef bookRef = pageRef.getBookRef();
		out.append('¿');
		encodeTextInXhtmlAttribute(bookRef.getDomain().toString(), out);
		out.append(':');
		encodeTextInXhtmlAttribute(bookRef.getPrefix(), out);
		encodeTextInXhtmlAttribute(pageRef.getPath().toString(), out);
		out.append('?');
	}

	/**
	 * Writes an href attribute with parameters.
	 * Adds contextPath to URLs that begin with a slash (/).
	 * Encodes the URL.
	 */
	public static void writeHref(
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		String href,
		URIParameters params,
		boolean absolute,
		boolean canonical
	) throws ServletException, IOException {
		if(href != null) {
			out.append(" href=\"");
			encodeTextInXhtmlAttribute(
				HttpServletUtil.buildURL(
					request,
					response,
					href,
					params,
					absolute,
					canonical
				),
				out
			);
			out.append('"');
		} else {
			if(params != null) throw new ServletException("parameters provided without href");
		}
	}

	/**
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 */
	public static <
		D extends AnyDocument<D>,
		__ extends Union_Palpable_Phrasing<D, __>,
		Ex extends Throwable
	> void writeLinkImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ content,
		Link link,
		LinkRendererBody<Ex> body
	) throws Ex, ServletException, IOException, SkipPageException {
		// Get the current capture state
		final CaptureLevel captureLevel = CurrentCaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			writeLinkImpl(
				servletContext,
				request,
				response,
				content,
				link.getDomain(),
				link.getBook(),
				link.getPagePath(),
				link.getElement(),
				link.getAllowGeneratedElement(),
				link.getAnchor(),
				link.getView(),
				link.getSmall(),
				link.getParams(),
				link.getAbsolute(),
				link.getCanonical(),
				link.getClazz(),
				body,
				captureLevel
			);
		}
	}

	/**
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 * @param domain    ValueExpression that returns String, evaluated at {@link CaptureLevel#META} or higher
	 * @param book      ValueExpression that returns String, evaluated at {@link CaptureLevel#META} or higher
	 * @param page      ValueExpression that returns String, evaluated at {@link CaptureLevel#META} or higher
	 * @param element   ValueExpression that returns String, evaluated at {@link CaptureLevel#BODY} only.
	 *                  Conflicts with {@code anchor}.
	 * @param anchor    ValueExpression that returns String, evaluated at {@link CaptureLevel#BODY} only.
	 *                  Conflicts with {@code element}.
	 * @param viewName  ValueExpression that returns String, evaluated at {@link CaptureLevel#BODY} only
	 * @param clazz     ValueExpression that returns Object, evaluated at {@link CaptureLevel#BODY} only
	 */
	public static <
		D extends AnyDocument<D>,
		__ extends Union_Palpable_Phrasing<D, __>,
		Ex extends Throwable
	> void writeLinkImpl(
		ServletContext servletContext,
		ELContext elContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ content,
		ValueExpression domain,
		ValueExpression book,
		ValueExpression page,
		ValueExpression element,
		boolean allowGeneratedElement,
		ValueExpression anchor,
		ValueExpression viewName,
		boolean small,
	    URIParameters params,
		boolean absolute,
		boolean canonical,
		ValueExpression clazz,
		LinkRendererBody<Ex> body
	) throws Ex, ServletException, IOException, SkipPageException {
		// Get the current capture state
		final CaptureLevel captureLevel = CurrentCaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			// Evaluate expressions
			DomainName domainObj;
			Path bookPath;
			try {
				domainObj = DomainName.valueOf(
					nullIfEmpty(
						resolveValue(domain, String.class, elContext)
					)
				);
				bookPath = Path.valueOf(
					nullIfEmpty(
						resolveValue(book, String.class, elContext)
					)
				);
			} catch(ValidationException e) {
				throw new ServletException(e);
			}
			String pageStr = resolveValue(page, String.class, elContext);
			String elementStr;
			String anchorStr;
			String viewNameStr;
			Object clazzObj;
			if(captureLevel == CaptureLevel.BODY) {
				elementStr = resolveValue(element, String.class, elContext);
				anchorStr = resolveValue(anchor, String.class, elContext);
				viewNameStr = resolveValue(viewName, String.class, elContext);
				clazzObj = resolveValue(clazz, Object.class, elContext);
			} else {
				elementStr = null;
				anchorStr = null;
				viewNameStr = null;
				clazzObj = null;
			}
			writeLinkImpl(
				servletContext,
				request,
				response,
				content,
				domainObj,
				bookPath,
				pageStr,
				elementStr,
				allowGeneratedElement,
				anchorStr,
				viewNameStr,
				small,
				params,
				absolute,
				canonical,
				clazzObj,
				body,
				captureLevel
			);
		}
	}

	/**
	 * @param  <D>   This document type
	 * @param  <__>  {@link Union_Palpable_Phrasing} provides both {@link A_factory} and {@link SPAN_factory}.
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 */
	private static <
		D extends AnyDocument<D>,
		__ extends Union_Palpable_Phrasing<D, __>,
		Ex extends Throwable
	> void writeLinkImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ content,
		DomainName domain,
		Path book,
		String page,
		String element,
		boolean allowGeneratedElement,
		String anchor,
		String viewName,
		boolean small,
	    URIParameters params,
		boolean absolute,
		boolean canonical,
		Object clazz,
		LinkRendererBody<Ex> body,
		CaptureLevel captureLevel
	) throws Ex, ServletException, IOException, SkipPageException {
		assert captureLevel.compareTo(CaptureLevel.META) >= 0;

		page = nullIfEmpty(page);

		if(domain != null && book == null) {
			throw new ServletException("book must be provided when domain is provided.");
		}

		final Node currentNode = CurrentNode.getCurrentNode(request);
		final Page currentPage = CurrentPage.getCurrentPage(request);

		// Use current page when page not set
		final PageRef targetPageRef;
		if(page == null) {
			if(book != null) throw new ServletException("page must be provided when book is provided.");
			if(currentPage == null) throw new ServletException("link must be nested in page when page attribute not set.");
			targetPageRef = currentPage.getPageRef();
		} else {
			targetPageRef = PageRefResolver.getPageRef(servletContext, request, domain, book, page);
		}
		// Add page links
		if(currentNode != null) currentNode.addPageLink(targetPageRef);
		if(captureLevel == CaptureLevel.BODY) {
			element = nullIfEmpty(element);
			anchor = nullIfEmpty(anchor);
			if(element != null && anchor != null) {
				throw new ServletException("May not provide both \"element\" and \"anchor\": element=\"" + element + "\", anchor=\"" + anchor + '"');
			}
			viewName = nullIfEmpty(viewName);
			// Evaluate expressions
			if(viewName == null) viewName = Link.DEFAULT_VIEW_NAME;

			// Find the view
			final SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			final HtmlRenderer htmlRenderer = HtmlRenderer.getInstance(servletContext);
			final View view = htmlRenderer.getViewsByName().get(viewName);
			if(view == null) throw new ServletException("View not found: " + viewName);
			final boolean isDefaultView = view.isDefault();

			// Capture the page
			final BookRef targetBookRef = targetPageRef.getBookRef();
			final Book targetBook = semanticCMS.getBook(targetBookRef);
			Page targetPage;
			if(!targetBook.isAccessible()) {
				// Book is not accessible
				targetPage = null;
			} else if(
				// Short-cut for element already added above within current page
				currentPage != null
				&& targetPageRef.equals(currentPage.getPageRef())
				&& (
					element == null
					|| currentPage.getElementsById().containsKey(element)
				)
			) {
				targetPage = currentPage;
			} else {
				// Capture required, even if capturing self
				targetPage = CapturePage.capturePage(
					servletContext,
					request,
					response,
					targetPageRef,
					element == null ? CaptureLevel.PAGE : CaptureLevel.META
				);
			}

			// Find the element
			Element targetElement;
			if(element != null && targetPage != null) {
				targetElement = targetPage.getElementsById().get(element);
				if(targetElement == null) throw new ServletException("Element not found in target page: " + element);
				if(!allowGeneratedElement && targetPage.getGeneratedIds().contains(element)) throw new ServletException("Not allowed to link to a generated element id, set an explicit id on the target element: " + element);
				if(targetElement.isHidden()) throw new ServletException("Not allowed to link to a hidden element: " + element);
			} else {
				targetElement = null;
			}

			// Write a link to the page

			PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
			Integer index = pageIndex==null ? null : pageIndex.getPageIndex(targetPageRef);

			StringBuilder href = new StringBuilder();
			if(element == null) {
				if(anchor == null) {
					// Link to page
					if(index != null && isDefaultView) {
						href.append('#');
						URIEncoder.encodeURIComponent(PageIndex.getRefId(index, null), href);
					} else {
						// TODO: Support multi-domain
						href.append(targetBookRef.getPrefix());
						href.append(targetPageRef.getPath());
						if(!isDefaultView) {
							boolean hasQuestion = href.lastIndexOf("?") != -1;
							href.append(hasQuestion ? "&view=" : "?view=");
							URIEncoder.encodeURIComponent(viewName, href);
						}
					}
				} else {
					// Link to anchor in page
					if(index != null && isDefaultView) {
						// Link to target in indexed page (view=all mode)
						href.append('#');
						URIEncoder.encodeURIComponent(PageIndex.getRefId(index, anchor), href);
					} else if(!absolute && currentPage!=null && currentPage.equals(targetPage) && isDefaultView) {
						// Link to target on same page
						href.append('#');
						URIEncoder.encodeURIComponent(anchor, href);
					} else {
						// Link to target on different page (or same page, absolute or different view)
						// TODO: Support multi-domain
						href.append(targetBookRef.getPrefix());
						href.append(targetPageRef.getPath());
						if(!isDefaultView) {
							boolean hasQuestion = href.lastIndexOf("?") != -1;
							href.append(hasQuestion ? "&view=" : "?view=");
							URIEncoder.encodeURIComponent(viewName, href);
						}
						href.append('#');
						URIEncoder.encodeURIComponent(anchor, href);
					}
				}
			} else {
				if(index != null && isDefaultView) {
					// Link to target in indexed page (view=all mode)
					href.append('#');
					URIEncoder.encodeURIComponent(PageIndex.getRefId(index, element), href);
				} else if(!absolute && currentPage!=null && currentPage.equals(targetPage) && isDefaultView) {
					// Link to target on same page
					href.append('#');
					URIEncoder.encodeURIComponent(element, href);
				} else {
					// Link to target on different page (or same page, absolute or different view)
					// TODO: Support multi-domain
					href.append(targetBookRef.getPrefix());
					href.append(targetPageRef.getPath());
					if(!isDefaultView) {
						boolean hasQuestion = href.lastIndexOf("?") != -1;
						href.append(hasQuestion ? "&view=" : "?view=");
						URIEncoder.encodeURIComponent(viewName, href);
					}
					href.append('#');
					URIEncoder.encodeURIComponent(element, href);
				}
			}
			// Add nofollow consistent with view and page settings.
			// TODO: Nofollow to missing books that cause targetPage to be null here?
			boolean nofollow = targetPage != null && !view.getAllowRobots(servletContext, request, response, targetPage);

			final String element_ = element;
			if(small) {
				SPAN<D, __> span = content.span();
				if(clazz != null) {
					span.clazz(clazz);
				} else {
					if(targetElement != null) {
						span.clazz(htmlRenderer.getLinkCssClass(targetElement));
					}
				}
				try (SPAN_c<D, __> span__ = span._c()) {
					if(body == null) {
						if(targetElement != null) {
							span__.text(targetElement);
						} else if(targetPage != null) {
							span__.text(targetPage.getTitle());
						} else {
							span__.text(text -> writeBrokenPath(targetPageRef, element_, text));
						}
						if(index != null) {
							span__.sup__(sup -> sup
								.text('[').text(index + 1).text(']')
							);
						}
					} else {
						body.doBody(false);
					}
					// TODO: Support multi-domain
					span__.sup__(sup -> sup
						.a()
							.href(
								HttpServletUtil.buildURL(
									request,
									response,
									href.toString(),
									params,
									absolute,
									canonical
								)
							)
							.rel(nofollow ? A.Rel.NOFOLLOW : null)
						.__(
							// TODO: Make [link] not copied during select/copy/paste, to not corrupt semantic meaning (and make more useful in copy/pasted code and scripts)?
							// TODO: https://stackoverflow.com/questions/3271231/how-to-exclude-portions-of-text-when-copying
							"[link]"
						)
					);
				}
			} else {
				A<D, __> a = content.a(
					HttpServletUtil.buildURL(
						request,
						response,
						href.toString(),
						params,
						absolute,
						canonical
					)
				);
				if(clazz != null) {
					a.clazz(clazz);
				} else {
					if(targetElement != null) {
						a.clazz(htmlRenderer.getLinkCssClass(targetElement));
					}
				}
				if(nofollow) a.rel(A.Rel.NOFOLLOW);
				// TODO: There is no a._c() for use in try.  Using WrappedException as a workaround
				try {
					a.__(a__ -> {
						if(body == null) {
							if(targetElement != null) {
								a__.text(targetElement);
							} else if(targetPage != null) {
								a__.text(targetPage.getTitle());
							} else {
								a__.text(text -> writeBrokenPath(targetPageRef, element_, text));
							}
							if(index != null) {
								a__.sup__(sup -> sup
									.text('[').text(index + 1).text(']')
								);
							}
						} else {
							try {
								body.doBody(false);
							} catch(SkipPageException e) {
								throw new WrappedException(e);
							}
						}
					});
				} catch(WrappedException e) {
					Throwable cause = e.getCause();
					if(cause instanceof SkipPageException) throw (SkipPageException)cause;
					throw e;
				}
			}
		} else {
			// Invoke body for any meta data, but discard any output
			if(body != null) body.doBody(true);
		}
	}

	/**
	 * Make no instances.
	 */
	private LinkRenderer() {
	}
}
