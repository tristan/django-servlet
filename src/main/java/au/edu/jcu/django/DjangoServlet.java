package au.edu.jcu.django;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyJavaInstance;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.python.core.imp;
import java.io.BufferedReader;
import org.python.core.PyException;
import org.python.core.PyTuple;
import org.python.core.PyModule;

public class DjangoServlet extends HttpServlet {
    private static Logger log = Logger.getLogger(DjangoServlet.class);

    private PythonInterpreter interp;
    private PyObject handler;
    private String rootp;

    public void init() throws ServletException {
	this.rootp = this.getServletContext().getRealPath("/");
	if (!this.rootp.endsWith(File.separator)) {
	    this.rootp += File.separator;
	}

	Properties props = new Properties();
	Properties baseProps = PySystemState.getBaseProperties();
	
	ServletContext context = this.getServletContext();
	Enumeration e = context.getInitParameterNames();
	while (e.hasMoreElements()) {
	    String n = (String)e.nextElement();
	    props.put(n, context.getInitParameter(n));
	}

	e = this.getInitParameterNames();
	while (e.hasMoreElements()) {
	    String n = (String)e.nextElement();
	    props.put(n, this.getInitParameter(n));
	}

	if (props.getProperty("python.home") == null &&
			      baseProps.getProperty("python.home") == null) {
		props.put("python.home", this.rootp + "WEB-INF" +
			  File.separator + "lib");
	}

	if (props.getProperty("django.handler") == null) {
	    throw new ServletException("property 'django.handler' needs to be set");
	}

	String djangoAppHome = props.getProperty("django.app.home");
	String djangoAppName = djangoAppHome.substring(djangoAppHome.lastIndexOf(File.separator)+1);
	djangoAppHome = djangoAppHome.substring(0, djangoAppHome.lastIndexOf(File.separator));
	if (djangoAppHome == null) {
	    throw new ServletException("property 'django.app.home' needs to be set");
	}

	PySystemState.initialize(baseProps, props, new String[0]);
	Py.getSystemState().path.append(new PyString(djangoAppHome));
	try {
	    // load site packages
	    imp.load("site");
	    // load os
	    PyObject environ = imp.load("os").__getattr__(new PyString("environ"));
	    HashMap<PyObject, PyObject> dsm = new HashMap<PyObject, PyObject>();
	    dsm.put((PyObject)new PyString("DJANGO_SETTINGS_MODULE"), (PyObject)new PyString(djangoAppName + ".settings"));
	    environ.invoke("update", (PyObject)new PyDictionary(dsm));
	    // load django app
	    imp.load(djangoAppName);
		     
	    // load django servlet handler
	    this.handler = imp.importName(props.getProperty("django.handler"),
					      false, null, new PyTuple(new PyString("handler")))
		.__findattr__("handler");
	} catch (PyException pye) {
	    pye.printStackTrace();
	    if (!Py.matchException(pye, Py.ImportError)) {
		log.error("error importing site");
	    }
	}
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp)
	throws ServletException, IOException {
    	this.handler.__call__(new PyJavaInstance(req), new PyJavaInstance(resp));
    }
}