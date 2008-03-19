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

    private PyObject handler;

    public void init() throws ServletException {

	// find the servlet root path
	String rootpath = this.getServletContext().getRealPath("/");
	if (!rootpath.endsWith(File.separator)) {
	    rootpath += File.separator;
	}

	// properties to pass to PySystemState.initialize
	Properties props = new Properties();
	Properties baseProps = PySystemState.getBaseProperties();
	
	// adding all the element from the servlet context
	// to the properties list
	ServletContext context = this.getServletContext();
	Enumeration e = context.getInitParameterNames();
	while (e.hasMoreElements()) {
	    String n = (String)e.nextElement();
	    props.put(n, context.getInitParameter(n));
	}

	// also add any initialisation parameters to the 
	// properties list
	e = this.getInitParameterNames();
	while (e.hasMoreElements()) {
	    String n = (String)e.nextElement();
	    props.put(n, this.getInitParameter(n));
	}

	// check if the python.home property exists
	// and if it doesn't then make it based on the
	// servlet root path
	if (props.getProperty("python.home") == null &&
			      baseProps.getProperty("python.home") == null) {
		props.put("python.home", rootpath + "WEB-INF" +
			  File.separator + "lib");
	}

	// make sure the django.handler property is set
	if (props.getProperty("django.handler") == null) {
	    throw new ServletException("property 'django.handler' needs to be set");
	}

	
	String djangoAppHome = props.getProperty("django.app.home");
	if (djangoAppHome == null) {
	    throw new ServletException("property 'django.app.home' needs to be set");
	}
	// get the django app name from the django.app.home variable
	String djangoAppName = djangoAppHome.substring(djangoAppHome.lastIndexOf(File.separator)+1);

	PySystemState.initialize(baseProps, props, new String[0]);
	PySystemState sys = Py.getSystemState();
	sys.path.append(new PyString(djangoAppHome));
	try {
	    // load site packages
	    imp.load("site");

	    // add DJANGO_SETTINGS_MODULE to os.environ 
	    /*
	    PyObject environ = imp.load("os").__getattr__(new PyString("environ"));
	    HashMap<PyObject, PyObject> dsm = new HashMap<PyObject, PyObject>();
	    dsm.put((PyObject)new PyString("DJANGO_SETTINGS_MODULE"), (PyObject)new PyString(djangoAppName + ".settings"));
	    environ.invoke("update", (PyObject)new PyDictionary(dsm)); */

	    // load django app settings module
	    PyObject settings = imp.load("settings");
	    // get the setup_environ function from the django management module
	    PyObject setup_environ = imp.importName("django.core.management", false, null,
						    new PyTuple(new PyString("setup_environ")))
		.__findattr__("setup_environ");
	    // call setup_environ
	    setup_environ.__call__(settings);
		     
	    // load django servlet handler
	    this.handler = imp.importName(props.getProperty("django.handler"),
					      false, null, new PyTuple(new PyString("handler")))
		.__findattr__("handler");
	} catch (PyException pye) {
	    log.error(pye.getMessage(), pye);
	    if (!Py.matchException(pye, Py.ImportError)) {
		log.error("error importing site");
	    }
	}
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp)
	throws ServletException, IOException {
	// call the handler
    	this.handler.__call__(new PyJavaInstance(req), new PyJavaInstance(resp));
    }
}