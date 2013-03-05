package soot.jimple.toolkits.javaee.model.servlet.http.io;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import soot.Hierarchy;
import soot.Scene;
import soot.SootClass;
import soot.jimple.toolkits.javaee.model.servlet.Filter;
import soot.jimple.toolkits.javaee.model.servlet.FilterMapping;
import soot.jimple.toolkits.javaee.model.servlet.Listener;
import soot.jimple.toolkits.javaee.model.servlet.Parameter;
import soot.jimple.toolkits.javaee.model.servlet.Servlet;
import soot.jimple.toolkits.javaee.model.servlet.Web;
import soot.jimple.toolkits.javaee.model.servlet.http.AbstractServlet;
import soot.jimple.toolkits.javaee.model.servlet.http.FileLoader;
import soot.jimple.toolkits.javaee.model.servlet.http.GenericServlet;
import soot.jimple.toolkits.javaee.model.servlet.http.HttpServlet;
import soot.jimple.toolkits.javaee.model.servlet.http.ServletSignatures;

/**
 * A reader for {@code web.xml} files.
 * 
 * @author Bernhard Berger
 */
public class WebXMLReader implements ServletSignatures {
	/**
	 * Logger instance.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(WebXMLReader.class);
	
	/**
	 * XML document.
	 */
	private Document doc;

	/**
	 * Provider for all necessary XPaths.
	 */
	private XPathExpressionProvider provider;

	/**
	 * Model to fill.
	 */
	private Web web;
	
	public Web readWebXML(final FileLoader loader, final Web web) throws Exception {
	    final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
	    domFactory.setNamespaceAware(true); // never forget this!
	    domFactory.setValidating(false);
	    domFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	    final DocumentBuilder builder = domFactory.newDocumentBuilder();

	    try {
		    final InputStream is = loader.getInputStream("WEB-INF/web.xml");
		    doc = builder.parse(is);
		    provider = ProviderFactory.create(doc);
			this.web = web;
			
			readFilters();
			readServlets(loader);
			readServletMappings();
			readListeners();
	    } catch(final FileNotFoundException e) {
	    	LOG.error("Cannot find web.xml in {}.", loader);
	    }
		
		return web;
	}

	private void readListeners() throws XPathException {
	    final XPathExpression listenerExpr = provider.getListenerExpression();

	    final NodeList listenerNodes = (NodeList)listenerExpr.evaluate(doc, XPathConstants.NODESET);
	    
	    for (int i = 0; i < listenerNodes.getLength(); i++) {
	        final Element node = (Element)listenerNodes.item(i);
	        
	        final NodeList children = node.getChildNodes();
	        
	        Listener listener = new Listener();
	        for(int j = 0; j < children.getLength(); j++) {
	        	if(!(children.item(j) instanceof Element)) {
	        		continue;
	        	}
	        	final Element child = (Element) children.item(j);
	        	
	        	final String attrName  = child.getNodeName();
	        	final String attrValue = child.getFirstChild().getNodeValue();
	        	
	        	if(attrName.equals("listener-class")) {
	        		listener.setClazz(attrValue);
	        	} else {
	        		LOG.warn("Unknown listener attribute {}.", attrName);
	        	}
	        }
	        
	        web.getListeners().add(listener);
	        
	        // check validity
	        if(listener.getClazz() == null) {
	        	LOG.warn("Listeners not configured correctly {}. ", listener);
	        }
	    }
	}

	/**
	 * Reads the filter settings and their mappings.
	 */
	private void readFilters() throws XPathException {
		LOG.info("Reading filters from web.xml");
		
		final XPathExpression filterExpr = provider.getFilterExpression();

		final NodeList filterNodes = (NodeList)filterExpr.evaluate(doc, XPathConstants.NODESET);

		LOG.info("Found {} filter nodes.", filterNodes.getLength());
		for (int i = 0; i < filterNodes.getLength(); i++) {
			final Element node = (Element)filterNodes.item(i);
			final NodeList children = node.getChildNodes();

			final Filter  filter = new Filter();
			for(int j = 0; j < children.getLength(); j++) {
				if(!(children.item(j) instanceof Element)) {
					continue;
				}

				final Element child = (Element) children.item(j);
				final String attrName  = child.getNodeName();
				final String attrValue = child.getFirstChild().getNodeValue();

				if(attrName.equals("filter-name")) {
					filter.setName(attrValue);
				} else if(attrName.equals("filter-class")) {
					filter.setClazz(attrValue);
				} else if(attrName.equals("display-name") || attrName.equals("description")) {
					// ignore
				} else {
					LOG.warn("Unknown filter attribute {}.", attrName);
				}
			}

			web.getFilters().add(filter);

			// check validity
			if(filter.getName() == null || filter.getClazz() == null) {
				LOG.error("Filter not configured correctly {}.", filter);
			}
		}

		final XPathExpression filterMappingExpr = provider.getFilterMappingExpression();
		final NodeList mappingNodes = (NodeList)filterMappingExpr.evaluate(doc, XPathConstants.NODESET);

		for (int i = 0; i < mappingNodes.getLength(); i++) {
			final Element node = (Element)mappingNodes.item(i);
			final NodeList children = node.getChildNodes();
			final FilterMapping mapping = new FilterMapping();

			for(int j = 0; j < children.getLength(); j++) {
				if(!(children.item(j) instanceof Element)) {
					continue;
				}

				final Element child = (Element) children.item(j);
				final String attrName  = child.getNodeName();
				final String attrValue = child.getFirstChild().getNodeValue();

				if(attrName.equals("filter-name")) {
					mapping.setFilter(web.getFilter(attrValue));
				} else if(attrName.equals("url-pattern")) {
					mapping.setURLPattern(attrValue.replace("*", ".*"));
				} else {
					LOG.warn("Unknown filter-mapping attribute {}.", attrName);
				}
			}

			web.getFilterMappings().add(mapping);
			LOG.info("Found filter mapping {}.", mapping);
		}
	}

	/**
	 * Reads the servlet-mappings.
	 */
	private void readServletMappings() throws XPathException {
	    final XPathExpression servletMappingExpr = provider.getServletMappingExpression();

	    final NodeList mappingNodes = (NodeList)servletMappingExpr.evaluate(doc, XPathConstants.NODESET);

	    for (int i = 0; i < mappingNodes.getLength(); i++) {
	        final Element node = (Element)mappingNodes.item(i);
	        
	        final NodeList children = node.getChildNodes();

	        String name = null;
	        String url  = null;
	        for(int j = 0; j < children.getLength(); j++) {
	        	if(!(children.item(j) instanceof Element)) {
	        		continue;
	        	}
	        	final Element child = (Element) children.item(j);
	        	
	        	final String attrName  = child.getNodeName();
	        	final String attrValue = child.getFirstChild().getNodeValue();
	        	
	        	if(attrName.equals("servlet-name")) {
	        		name = attrValue;
	        	} else if(attrName.equals("url-pattern")) {
	        		url = attrValue;
	        	} else {
	        		LOG.warn("Unknown servlet-mapping attribute {}.", attrName);
	        	}
	        }

	        try {
	        	final Servlet servlet = web.getServlet(name);

	        	web.bindServlet(servlet, url);
	        } catch(final IllegalArgumentException e) {
	        	LOG.warn("Cannot bind wildcard urls");
	        }
	    }
	}

	/**
	 * Reads all servlets
	 * @param loader 
	 */
	private void readServlets(FileLoader loader) throws XPathException {
		LOG.info("Reading servlets from web.xml");
		
		final Set<Servlet> servlets = web.getServlets();
		
	    final XPathExpression servletExpr = provider.getServletExpression();

	    final NodeList servletNodes = (NodeList)servletExpr.evaluate(doc, XPathConstants.NODESET);

	    LOG.info("Found {} servlet nodes.", servletNodes.getLength());
	    for (int i = 0; i < servletNodes.getLength(); i++) {
	        final Element node = (Element)servletNodes.item(i);
	        
	        final NodeList children = node.getChildNodes();
	        final AbstractServlet servlet = newServlet(node);

	        for(int j = 0; j < children.getLength(); j++) {
	        	if(!(children.item(j) instanceof Element)) {
	        		continue;
	        	}
	        	final Element child = (Element) children.item(j);
	        	
	        	final String attrName  = child.getNodeName();
	        	final String attrValue = child.getFirstChild().getNodeValue();
	        	
	        	if(attrName.equals("servlet-name")) {
	        		servlet.setName(attrValue);
	        	} else if(attrName.equals("servlet-class")) {
	        		servlet.setClazz(attrValue);
				} else if(attrName.equals("init-param")) {
					final Parameter parameter = new Parameter();
					final NodeList paramNodes = child.getChildNodes();
					for(int k = 0; k < paramNodes.getLength(); ++k) {
						if(!(paramNodes.item(k) instanceof Element)) {
							continue;
						}

						final Element paramNode = (Element)paramNodes.item(k);

						if(paramNode.getNodeName().equals("param-name")) {
							parameter.setName(paramNode.getFirstChild().getNodeValue());
						} else if(paramNode.getNodeName().equals("param-value")) {
							parameter.setValue(paramNode.getFirstChild().getNodeValue());
						}
					}
					servlet.getParameters().add(parameter);
	        	} else {
	        		LOG.warn("Unknown servlet attribute {}.", attrName);
	        	}
	        }
			servlet.setLoader(loader);	        
	        servlets.add(servlet);
	        
	        // check validity
	        if(servlet.getName() == null || servlet.getClazz() == null) {
	        	LOG.error("Servlet not configured correctly {}.", servlet);
	        }
	    }
	}

	/**
	 * Creates a new model servlet instance (either GenericServlet or HttpServlet).
	 * 
	 * @param servletNode XML servlet node.
	 * 
	 * @return A new instance.
	 */
	private static AbstractServlet newServlet(final Element servletNode) {
		final NodeList classNodes = servletNode.getElementsByTagName("servlet-class");
		
		if(classNodes.getLength() == 0) {
			LOG.error("Cannot find servlet-class for servlet {}.", servletNode);
			return new GenericServlet();
		}
		
		if(classNodes.getLength() > 1) {
			LOG.warn("Multiple classes for servlet {} found. Choosing first one.", servletNode);
		}
		
    	final String className = classNodes.item(0).getFirstChild().getNodeValue();

    	final SootClass implementationClass = Scene.v().forceResolve(className, SootClass.HIERARCHY);

	    final Hierarchy cha = Scene.v().getActiveHierarchy();
	    final SootClass servletClass = Scene.v().getSootClass(HTTP_SERVLET_CLASS_NAME);
	    final SootClass genericClass = Scene.v().getSootClass(GENERIC_SERVLET_CLASS_NAME);
	    
        if (cha.isClassSubclassOf(implementationClass, servletClass)){
        	LOG.info("Found http servlet class {}.", implementationClass);
            return new HttpServlet();
        } else if(cha.isClassSubclassOf(implementationClass, genericClass)) {
        	LOG.info("Found generic servlet class {}.", implementationClass);
            return new GenericServlet();
        } else {
        	LOG.warn("Class '{}' is neither a http nor a generic servlet.", implementationClass);
        	return new GenericServlet();
        }
	}
}
