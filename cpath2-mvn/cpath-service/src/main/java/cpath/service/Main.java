// $Id$
//------------------------------------------------------------------------------
/** Copyright (c) 2010 Memorial Sloan-Kettering Cancer Center.
 **
 ** This library is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** Memorial Sloan-Kettering Cancer Center
 ** has no obligations to provide maintenance, support,
 ** updates, enhancements or modifications.  In no event shall
 ** Memorial Sloan-Kettering Cancer Center
 ** be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** Memorial Sloan-Kettering Cancer Center
 ** has been advised of the possibility of such damage.  See
 ** the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this library; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 **/
package cpath.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import cpath.service.CPathService.ResultMapKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import static cpath.config.CPathSettings.*;

/**
 * Provides command line query services.
 */
public class Main  {
	private static final Log LOG = LogFactory.getLog(Main.class);
	
    // COMMAND Enum
    public static enum COMMAND 
    {
    	VALIDATION_REPORT("-validation-report"),
    	;

        private String command;
        
        COMMAND(String command) { this.command = command; }

        public String toString() { return command; }
    }

    // command, command parameter
    private COMMAND command;
    private String[] commandParameters;
    
	
	private CPathService getService() {
		ApplicationContext context =
            new ClassPathXmlApplicationContext(new String [] { 	
            		"classpath:applicationContext-cpathDAO.xml",
        			"classpath:applicationContext-whouseDAO.xml",
        			"classpath:applicationContext-whouseMolecules.xml",
        			"classpath:applicationContext-whouseProteins.xml",
        			"classpath:applicationContext-cvRepository.xml",
        			"classpath:applicationContext-cpathService.xml"});
		return (CPathService) context.getBean("service");
	}
	
	
    public void setCommandParameters(String[] args) {
		this.commandParameters = args;
        // parse args
        parseArgs(args);
	}
    
    
    private void parseArgs(final String[] args) {
        boolean validArgs = true;
 
        if(args[0].equals(COMMAND.VALIDATION_REPORT.toString())) {
			if (args.length != 3) {
				validArgs = false;
			} else {
				this.command = COMMAND.VALIDATION_REPORT;
				this.commandParameters = new String[] {args[1], args[2]};
			} 
        } 
        else {
            validArgs = false;
        }
        
        if(!validArgs) {
        	System.err.println("Missing args!" + args.toString());
        	System.exit(-1);
        }
    }


    public void run() {
        try {
            switch (command) {
            case VALIDATION_REPORT:
                getValidationReport(commandParameters[0], commandParameters[1]);
				break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


	private void getValidationReport(final String provider, 
    		final String output) throws IOException 
    {
    	if(LOG.isInfoEnabled()) {
    		LOG.info("Getting validation report for " + provider
    				+ "...");
    	}
    	Map<ResultMapKey, Object> res = getService().getValidationReport(provider);
    	if(res.containsKey(ResultMapKey.ERROR)) {
    		System.err.println(res.get(ResultMapKey.ERROR));
    	} else if(res.containsKey(ResultMapKey.DATA)) {
    		String report = (String) res.get(ResultMapKey.DATA);
    		FileOutputStream fileOutputStream = new FileOutputStream(output);
    		fileOutputStream.write(report.getBytes("UTF-8"));
    		fileOutputStream.close();
    	}
	}
	
	
	private static void usage() {
		StringBuffer toReturn = new StringBuffer();
		toReturn.append(Main.class.getCanonicalName()).append(" <command> <one or more args>" + NEWLINE);
		toReturn.append("commands:" + NEWLINE);
		toReturn.append(COMMAND.VALIDATION_REPORT.toString() + " <provider,output>" + NEWLINE);
		System.err.println(toReturn.toString());
		System.exit(-1);
	}

    /**
     * The big deal main.
     * 
     * @param args String[]
     */    
    public static void main(String[] args) throws Exception {
        // sanity check
        if (args.length == 0) {
			Main.usage(); //exits
        }
    	
    	String home = System.getenv(HOME_VARIABLE_NAME);
    	if (home==null) {
            System.err.println("Please set " + HOME_VARIABLE_NAME 
            	+ " environment variable " +
            	" (point to a directory where cpath.properties, etc. files are placed)");
            System.exit(-1);
    	}
    	
    	// configure logging
    	PropertyConfigurator.configure(home + File.separator 
    			+ "log4j.properties");
    	// set JVM property to be used by other modules (in spring context)
    	System.setProperty(HOME_VARIABLE_NAME, home);
    	
		Main app = new Main();
		app.setCommandParameters(args);
        app.run();
        
		System.exit(0);
    }
    
}
