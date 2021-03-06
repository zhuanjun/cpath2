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
package cpath.admin;

import static cpath.config.CPathSettings.*;
import cpath.config.CPathSettings;
import cpath.dao.*;
import cpath.importer.Merger;
import cpath.importer.PreMerger;
import cpath.jpa.Metadata;
import cpath.jpa.Metadata.METADATA_TYPE;
import cpath.jpa.MetadataRepository;
import cpath.service.Analysis;
import cpath.service.BiopaxConverter;
import cpath.service.CPathService;
import cpath.service.Indexer;
import cpath.service.OutputFormat;
import cpath.service.SearchEngine;
import cpath.service.Searcher;
import cpath.service.jaxb.*;

import org.biopax.paxtools.controller.ModelUtils;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.io.*;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.pattern.miner.BlacklistGenerator;
import org.biopax.paxtools.pattern.util.Blacklist;
import org.biopax.validator.api.Validator;
import org.h2.tools.Csv;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Class which provides cpath2 command line
 * access for the system administrators and 
 * user scripts.
 */
public final class Admin {
	private static final Logger LOG = LoggerFactory.getLogger(Admin.class);
	
	private static final CPathSettings cpath = CPathSettings.getInstance();

    // Cmd Enum
    public static enum Cmd {
        // command types
        FETCH_METADATA("-fetch-metadata"),
		FETCH_DATA("-fetch-data"),
		PREMERGE("-premerge"),
		CREATE_WAREHOUSE("-create-warehouse"),			
		MERGE("-merge"),
    	INDEX("-index"),
        CREATE_DOWNLOADS("-create-downloads"),
		EXPORT("-export"),
        CONVERT("-convert"),
        LOG("-log"),
        ANALYSIS("-run-analysis"), //e.g., custom post-fix or model summary/statistic
		;

        // string ref for readable name
        private String command;
        
        // contructor
        Cmd(String command) { this.command = command; }

        // method to get enum readable name
        @Override
        public String toString() { return command; }
    }
    
 
    /**
     * The big deal main.
     * 
     * @param params String[]
     */    
    public static void main(String[] params) throws Exception {
    	// "CPATH2_HOME" system option must be set (except for unit testing)!
    	Assert.hasText(cpath.property(HOME_DIR)); 

    	if(!Charset.defaultCharset().equals(Charset.forName("UTF-8")))
    		if(LOG.isWarnEnabled())
    			LOG.warn("Default Charset, " + Charset.defaultCharset() 
    				+ " (is NOT 'UTF-8'...)");
    	
    	
    	// Cleanup arguments by removing empty/null strings from the end
    	// - possible result of calling this method from a script
    	List<String> argl = new ArrayList<String>();
    	for(String a : params) {
    		if(a == null || a.isEmpty() || a.equalsIgnoreCase("null"))
    			break;
    		else
    			argl.add(a);
    	}
    	final String[] args = argl.toArray(new String[]{});
  		argl = null;
    	
    	LOG.debug("Command-line arguments were: " + Arrays.toString(args));
    	
    	// sanity check
        if (args.length == 0 || args[0].isEmpty()) {
            System.err.println("Missing args to Admin.");
			System.err.println(Admin.usage());
            System.exit(-1);
        }
    	

        // create the data dir. inside the home dir. if it does not exist
		File dir = new File(cpath.dataDir());

		if (args[0].equals(Cmd.INDEX.toString())) {
			
			index();
			
		} else if (args[0].equals(Cmd.FETCH_METADATA.toString())) {
			
			if (args.length == 1) {
				fetchMetadata("file:" + cpath.property(PROP_METADATA_LOCATION));
			} else {
				fetchMetadata(args[1]);
			}
			
		} else if (args[0].equals(Cmd.CREATE_WAREHOUSE.toString())) {
			
				createWarehouse();
			
		} else if (args[0].equals(Cmd.PREMERGE.toString())) {
			
			if (args.length > 1)
				runPremerge(args[1]);
			else
				// command without extra parameter
				runPremerge(null);
			
		} else if (args[0].equals(Cmd.MERGE.toString())) {
			boolean force = false;
			for (int i = 1; i < args.length; i++) {
				if ("--force".equalsIgnoreCase(args[i])) {
					force = true;
				}
			}

			runMerge(force);
			
		} else if (args[0].equals(Cmd.EXPORT.toString())) {
			//(the first args[0] is the command name, the second - must be output file)
			if (args.length < 2)
				fail(args, "must provide at least an output file name (other parameters are optional).");
			else {
				String[] uris = new String[] {};
				String[] datasources = new String[] {};
				String[] types = new String[] {};
				boolean absoluteUris = false;
							
				for(int i=2; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("--output-absolute-uris"))
						absoluteUris = true;
					else if(args[i].toLowerCase().startsWith("--datasources="))
						datasources = args[i].substring(14).split(",");
					else if(args[i].toLowerCase().startsWith("--types="))
						types = args[i].substring(8).split(",");
					else if(args[i].toLowerCase().startsWith("--uris="))
						uris = args[i].substring(7).split(",");
					else 
						LOG.error("Skipped unrecognized argument: " + args[i]);
				}
				
				exportData(args[1], uris, absoluteUris, datasources, types);
			}
			
		} else if (args[0].equals(Cmd.CONVERT.toString())) {
			if (args.length < 4)
				fail(args, "provide at least three arguments.");
			OutputStream fos = new FileOutputStream(args[2]);
			OutputFormat outputFormat = OutputFormat.valueOf(args[3]);	
			Model model = (new SimpleIOHandler()).convertFromOWL(biopaxStream(args[1]));
			
			String idTypeOrLayout = (args.length >= 5) ? args[4] : null;
			
			if(args.length >= 6 && Boolean.parseBoolean(args[5])) {
				LOG.info("Merging equivalent interactions in the model...");
				ModelUtils.mergeEquivalentInteractions(model);
			}
			
			LOG.info("Start converting to " + outputFormat);
			convert(model, outputFormat, fos, idTypeOrLayout);
			
		} else if (args[0].equals(Cmd.CREATE_DOWNLOADS.toString())) {

			createDownloads();
		
		} else if (args[0].equals(Cmd.LOG.toString())) {	
			//options: --export/import filename | --update/merge/delete type:name,type:name,type... 
			// (names are case-sensitive)
			
			if (args.length < 2) //fail fast
				fail(args, "No options provided.");
			
			if("--export".equalsIgnoreCase(args[1])) {
				if (args.length < 3) 
					fail(args, "No filename provided with --export option.");	
				else exportLog(args[2]);
			} else if("--import".equalsIgnoreCase(args[1])) {
				if (args.length < 3) 
					fail(args, "No filename provided with --import option.");	
				else importLog(args[2]);
			} else if("--merge".equalsIgnoreCase(args[1])) {	
				fail(args, "Not implemented yet."); 
				//TODO implement: merge log events (might not worth it - can use H2 Console and SQL instead, or a text editor)
			} else if("--delete".equalsIgnoreCase(args[1])) {
				fail(args, "Not implemented yet."); 
				//TODO implement: delete log events (might not worth it - can use H2 Console and SQL instead, or a text editor)
			} else {
				fail(args, "Unknown option: " + args[1]);
			}
			
		} else if (args[0].equals(Cmd.ANALYSIS.toString())) {	
			
			if (args.length < 2) 
				fail(args, "No Analysis implementation class provided.");	
			else if(args.length == 2)
				executeAnalysis(args[1], true);
			else if(args.length > 2 && "--update".equalsIgnoreCase(args[2]))
				executeAnalysis(args[1], false);
		
		} else {
			System.err.println(usage());
		}

		// required because MySQL Statement
		// Cancellation Timer thread is still running
		System.exit(0);
    }    

	/**
     * Executes a code that uses or edits the main BioPAX model.
     * 
     * @param analysisClass a class that implements {@link Analysis} 
     * @param readOnly
     */
    public static void executeAnalysis(String analysisClass, boolean readOnly) {
    	
    	if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
    	
    	Analysis analysis = null;
		try {
			Class<Analysis> c = (Class<Analysis>) Class.forName(analysisClass);
			analysis = c.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    	
		Model model = CPathUtils.loadMainBiopaxModel();
 		analysis.execute(model);
 		
 		if(!readOnly) { //replace the main BioPAX model archive
 			try {			
 				new SimpleIOHandler(BioPAXLevel.L3).convertToOWL(model, 
 					new GZIPOutputStream(new FileOutputStream(
 							CPathSettings.getInstance().mainModelFile())));
 			} catch (Exception e) {
 				throw new RuntimeException("Failed updating the main BioPAX archive!", e);
 			}
 			
 			LOG.warn("The main BioPAX model was modified; "
 				+ "do not forget to re-index, update counts, re-export other files, etc.");
 		}
 			
	}

    public static void exportLog(String filename) throws IOException {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
 		
 		Connection conn = null;
 		try {
			Class.forName("org.h2.Driver");
			conn = DriverManager.getConnection("jdbc:h2:"+cpath.homeDir()+"/cpath2", "sa", "");
			new Csv()
				.write(conn, filename, "select * from logentity", "UTF-8");
			LOG.info("Saved current access log DB to " + filename);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {conn.close();} catch (Exception e) {}
		}
	}

    public static void importLog(String filename) throws IOException {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
 		
 		//load cpath2 access db from a CSV file
 		String backup = cpath.dataDir() + File.separator + "logentity.csv.bak";
 		Connection conn = null;
 		try {
 			Class.forName("org.h2.Driver");
			conn = DriverManager.getConnection("jdbc:h2:"+cpath.homeDir()+"/cpath2", "sa", "");
			
			//backup
			new Csv().write(conn, backup, "select * from logentity", "UTF-8");
			LOG.info("Saved current access log DB to " + backup);
			
			//clear all existing data
			conn.createStatement().executeUpdate("delete from logentity;");
			LOG.info("Cleared access log DB");
			
			conn.createStatement().executeUpdate("insert into logentity " +
	 				"select * from CSVREAD('"+ filename +"')");	 		
	 		conn.commit();
	        LOG.info("Imported access log entries from " + filename);
		} catch (Exception e) {
			try {conn.rollback(); conn.close();} catch (Exception ex) {}
			throw new RuntimeException(e);
		} finally {
			try {conn.close();} catch (Exception e) {}
		}
	}
    
	private static void fail(String[] args, String details) {
        throw new IllegalArgumentException(
        	"Invalid cpath2 command: " +  Arrays.toString(args)
        	+ "; " + details);		
	}
	
    
	/**
     * Builds a new BioPAX full-text index,creates the black list or ubiquitous molecules,
	 * and calculates/updates the total no. of pathways, interactions, physical entities in the main db.
     * 
	 * @throws IOException
     * @throws IllegalStateException when not in maintenance mode
     */
    public static void index() throws IOException {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");

		LOG.info("index: importing the main BioPAX model from the archive...");
		Model model = CPathUtils.loadMainBiopaxModel();
		LOG.info("index: the model is ready.");

		Indexer indexer = new SearchEngine(model, CPathSettings.getInstance().indexDir());
		LOG.info("index: indexing...");
		indexer.index();
 		LOG.info("index: done indexing.");

		LOG.info("index: blacklisting...");
		createBlacklist(model);
		LOG.info("index: blacklist done.");

		LOG.info("index: counting the no. pathways, interactions and physical entities...");
		updateCounts(model);

		LOG.info("index: all done.");
 	}

    
	/*
	 * Updates counts of pathways, etc. and saves in the Metadata table.
	 * 
     * This depends on the full-text index, which must have been created already
     * (otherwise, results will be wrong).
     */
	private static void updateCounts(Model model) {

		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				new String[] { "classpath:META-INF/spring/applicationContext-jpa.xml" });

		MetadataRepository metadataRepo = (MetadataRepository) context.getBean(MetadataRepository.class);
		
		// initialize the search engine
		Searcher searcher = new SearchEngine(model, CPathSettings.getInstance().indexDir());

		// prepare a list of all pathway type metadata to update
		List<Metadata> pathwayMetadata = new ArrayList<Metadata>();
		for (Metadata md : metadataRepo.findAll())
			if (!md.isNotPathwayData())
				pathwayMetadata.add(md);

		// for each non-warehouse metadata entry, update counts of pathways, etc.
		for (Metadata md : pathwayMetadata) {
			String name = md.standardName();
			String[] dsUrisFilter = new String[] { md.getUri() };

			SearchResponse sr = (SearchResponse) searcher.search("*", 0,
					Pathway.class, dsUrisFilter, null);
			md.setNumPathways(sr.getNumHits());
			LOG.info(name + " - pathways: " + sr.getNumHits());

			sr = (SearchResponse) searcher.search("*", 0, Interaction.class,
					dsUrisFilter, null);
			md.setNumInteractions(sr.getNumHits());
			LOG.info(name + " - interactions: " + sr.getNumHits());

			Integer count;
			sr = (SearchResponse) searcher.search("*", 0, PhysicalEntity.class,
					dsUrisFilter, null);
			count = sr.getNumHits();
			sr = (SearchResponse) searcher.search("*", 0, Gene.class,
					dsUrisFilter, null);
			count += sr.getNumHits();		
			md.setNumPhysicalEntities(count);
			LOG.info(name + " - molecules, complexes and genes: " + count);
		}

		metadataRepo.save(pathwayMetadata);

		context.close();
	}
    
	
	/*
     * Generates cpath2 graph query blacklist file
     * (to exclude ubiquitous small molecules, like ATP).
     */
    private static void createBlacklist(Model model) throws IOException {

		BlacklistGenerator gen = new BlacklistGenerator();
		Blacklist blacklist = gen.generateBlacklist(model);

		// Write all the blacklisted ids to the output
		try {		
			blacklist.write(new FileOutputStream(cpath.blacklistFile()));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Failed creating the file: " 
					+ cpath.blacklistFile(), e);
		} 
    }

    
    /**
     * Performs cpath2 Merge stage.
     * @param force
     * 
     * @throws IllegalStateException when not maintenance mode
     */
    public static void runMerge(boolean force) {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
    	
		//disable 2nd level hibernate cache (ehcache)
		// otherwise the merger eventually fails with a weird exception
		// (this probably depends on the cache config. parameters)
		System.setProperty("net.sf.ehcache.disabled", "true");
		ClassPathXmlApplicationContext context = 
			new ClassPathXmlApplicationContext(new String[] {
					"classpath:META-INF/spring/applicationContext-jpa.xml"
			});
		
		LOG.info("runMerge: --force=" + force);
		
		//create CPathService bean that provides access to JPA repositories (metadata, mapping)
		CPathService service = context.getBean(CPathService.class);
		Merger merger = new Merger(service, force);		
		merger.merge(); //at the end, it saves the resulting integrated main biopax model to a special file at known location.
		
		context.close();		
	}

	
    /**
     * Performs cpath2 Premerger stage:
     * pathway data cleaning, converting, validating, normalizing.
     * 
     * @param provider
     * @throws IllegalStateException when not maintenance mode
     */
	public static void runPremerge(String provider) {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");		
		
        LOG.info("runPremerge: provider=" + provider + " - initializing (DAO, validator, premerger)...");
        
		ClassPathXmlApplicationContext context =
            new ClassPathXmlApplicationContext(new String [] { 	
            		"classpath:META-INF/spring/applicationContext-jpa.xml", 
            		"classpath:META-INF/spring/applicationContext-validator.xml"
            		});
		CPathService service = context.getBean(CPathService.class);
		Validator validator = (Validator) context.getBean("validator");
        PreMerger premerger = new PreMerger(service, validator, provider);       
        LOG.info("runPremerge: provider=" + provider + " - running...");
        premerger.premerge();
        
        context.close(); 
	}

	
    /**
     * Creates cpath2 Warehouse and id-mapping tables.
     * 
     * @throws IllegalStateException when not maintenance mode
     */
	public static void createWarehouse() {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
		System.setProperty("hibernate.hbm2ddl.auto", "update");
		System.setProperty("net.sf.ehcache.disabled", "true");
		ClassPathXmlApplicationContext context =
            new ClassPathXmlApplicationContext(new String [] { 	
            		"classpath:META-INF/spring/applicationContext-jpa.xml"
            		});
		CPathService service = context.getBean(CPathService.class);
		PreMerger premerger = new PreMerger(service, null, null);
        premerger.buildWarehouse();     
        context.close(); 
        
        //back to read-only schema mode (useful when called from the web admin app)
        System.setProperty("hibernate.hbm2ddl.auto", "validate");
	}

	
	/**
     * Helper function to get provider metadata.
     *
     * @param location String PROVIDER_URL or local file.
     * @throws IOException, IllegalStateException (when not maintenance mode)
     */
    public static void fetchMetadata(final String location) throws IOException {
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
		System.setProperty("hibernate.hbm2ddl.auto", "update");
		
		ClassPathXmlApplicationContext context =
            new ClassPathXmlApplicationContext(new String [] { 	
            		"classpath:META-INF/spring/applicationContext-jpa.xml", 
            		});
		CPathService service = context.getBean(CPathService.class);
        // grab the data
        service.addOrUpdateMetadata(location);       
        context.close(); 
        
        //back to read-only schema mode (useful when called from the web admin app)
        System.setProperty("hibernate.hbm2ddl.auto", "validate");
    }


	/**
	 * Exports a cpath2 BioPAX sub-model or full model to the specified file.
	 * 
	 * @param output - output BioPAX file name (path)
	 * @param uris - optional, the list of valid (existing) URIs to extract a sub-model
	 * @param outputAbsoluteUris - if true, all URIs in the BioPAX elements 
	 * and properties will be absolute (i.e., no local relative URIs, 
	 * such as rdf:ID="..." or rdf:resource="#...", will be there in the output file.) 
	 * @param datasources filter by data source if 'uris' is not empty
	 * @param types filter by biopax type if 'uris' is not empty
	 * 
	 * @throws IOException, IllegalStateException (in maintenance mode)
	 */
	public static void exportData(final String output, String[] uris, boolean outputAbsoluteUris, 
			String[] datasources, String[] types) throws IOException 
	{	
		if(uris == null) 
			uris = new String[]{};
		if(datasources == null) 
			datasources = new String[]{};
		if(types == null) 
			types = new String[]{};
		
		//load the model
		Model model = CPathUtils.loadMainBiopaxModel();
		LOG.info("Loaded the BioPAX Model");

		if(uris.length == 0 && (datasources.length > 0 || types.length > 0)) {
					
			// initialize the search engine
			Searcher searcher = new SearchEngine(model, CPathSettings.getInstance().indexDir());

			Collection<String> selectedUris = new HashSet<String>();
			
			if(types.length>0) {
				//collect biopax object URIs of the specified types and sub-types, and data sources if specified
				//(child biopax elements will be auto-included during the export to OWL)
				for(String bpInterfaceName : types) {
					selectedUris.addAll(findAllUris(searcher, 
							biopaxTypeFromSimpleName(bpInterfaceName), datasources, null)); 
				}
			} else {
				//collect all Entity URIs filtered by the not empty data sources list
				//(child Gene, PhysicalEntity, UtilityClass biopax elements will be auto-included 
				// during the export to OWL; we do not want to export dangling Genes, PEs, etc., except for Complexes...)
				selectedUris.addAll(findAllUris(searcher, Pathway.class, datasources, null));
				selectedUris.addAll(findAllUris(searcher, Interaction.class, datasources, null)); 
				selectedUris.addAll(findAllUris(searcher, Complex.class, datasources, null)); 
			}
		
			uris = selectedUris.toArray(new String[] {});
		}
		
		OutputStream os = new FileOutputStream(output);
		// export a sub-model from the main biopax database
		SimpleIOHandler sio = new SimpleIOHandler(BioPAXLevel.L3);
		sio.absoluteUris(outputAbsoluteUris);
		sio.convertToOWL(model, os, uris);
	}	
	
			
	private static Class<? extends BioPAXElement> biopaxTypeFromSimpleName(String type) 
	{	
		// 'type' (a BioPAX L3 interface class name) is case insensitive 
		for(Class<? extends BioPAXElement> c : SimpleEditorMap.L3
				.getKnownSubClassesOf(BioPAXElement.class)) 
		{
			if(c.getSimpleName().equalsIgnoreCase(type)) {
				if(c.isInterface() && BioPAXLevel.L3.getDefaultFactory().getImplClass(c) != null)
					return c; // interface
			}
		}
		throw new IllegalArgumentException("Illegal BioPAX class name '" + type);
	}


	private static String usage() 
	{
		final String NEWLINE = System.getProperty ( "line.separator" );
		StringBuilder toReturn = new StringBuilder();
		toReturn.append("Usage: <-command_name> [<command_args...>] " +
				"(- parameters within the square braces are optional.)" + NEWLINE);
		toReturn.append("commands:" + NEWLINE);
		// data import (instance creation) pipeline :
		toReturn.append(Cmd.FETCH_METADATA.toString() + " <url>" + NEWLINE);
		toReturn.append(Cmd.PREMERGE.toString() + " [<metadataId>]" + NEWLINE);
		toReturn.append(Cmd.CREATE_WAREHOUSE.toString() + NEWLINE);			
		toReturn.append(Cmd.MERGE.toString() + " [--force] (merge all pathway data; overwrites the main biopax model archive)"+ NEWLINE);
		toReturn.append(Cmd.INDEX.toString() + " (build new full-text index of the main merged BioPAX db;" +
				"create blacklist.txt in the downloads directory; re-calculates the no. pathways, molecules and " +
        		"interactions per data source)" + NEWLINE);
        toReturn.append(Cmd.CREATE_DOWNLOADS.toString() + " (creates cpath2 BioPAX DB archives using several " +
        	"data formats, and also split by data source, organism)"  + NEWLINE);        
        // other useful (utility) commands
		toReturn.append(Cmd.EXPORT.toString() + " <output> [--uris=<uri,uri,..>] [--output-absolute-uris] [--datasources=<nameOrUri,..>] [--types=<interface,..>]" +
				"(writes to the output file the main BioPAX model, or a sub-model if the list of URIs or filter option is provided; " +
				"when --output-absolute-uris flag is present, all URIs there in the output BioPAX will be absolute; " +
				"when --datasources or/and --types flag is set, and 'uri' list is not, then the result model " +
				"will contain BioPAX elements that pass the filter by data source and/or type)" + NEWLINE); 
		toReturn.append(Cmd.CONVERT.toString() + " <biopax-file(.owl|.gz)> <output-file> <output format> [<option>] [true]"
				+ "(can convert any of previously generated in the downloads directory BioPAX file "
				+ "to another cPath2 output format: SBGN, GSEA, BINARY_SIF, EXTENDED_BINARY_SIF "
				+ "using also generated blacklist.txt; <option> is optional: "
				+ "boolean TRUE/FALSE for the SBGN converter - whether to apply the default layout, and - identifiers type for all other formats,"
				+ "e.g., one can use 'uniprot' with SIF (where the default is HGNC), or - 'hgnc' with GSEA (the defaut is UniProt);"
				+ "finally, optional 'true' (the last parameter) can be used to enable "
				+ "merging equivalent interactions before the analysis)." + NEWLINE);
		toReturn.append(Cmd.LOG.toString() + " --export/import <filename> | --merge/delete type1:name1,type2:name2,type3,.. "
				+ "(Exports/imports the cpath2 internal assess log db to/from the specified CSV file; "
				+ "--import clears and rewrites the log db; "
				+ "--merge or --delete merges/deletes log db rows for the specified type:name log events)" + NEWLINE);
		toReturn.append(Cmd.ANALYSIS.toString() + " <classname> [--update] (execute custom code within the cPath2 BioPAX database; " +
				"if --update is set, one then should re-index and generate new 'downloads')" + NEWLINE);
		
		return toReturn.toString();
	}

	
    /*
     * Converts a BioPAX file to other formats.
     */
	private static void convert(Model model, OutputFormat outputFormat, 
			OutputStream output, Object... params) throws IOException 
	{
		Resource blacklist = new DefaultResourceLoader().getResource("file:" + cpath.blacklistFile());
		BiopaxConverter converter = new BiopaxConverter(new Blacklist(blacklist.getInputStream()));
		converter.convert(model, outputFormat, output, params);
		output.flush();
	}

	
    @SuppressWarnings("resource")
	private static InputStream biopaxStream(String biopaxFile) throws IOException {
		return (biopaxFile.endsWith(".gz"))
			? new GZIPInputStream(new FileInputStream(biopaxFile)) 
			: new FileInputStream(biopaxFile);
	}

    
    /**
     * Create cpath2 downloads 
     * (exports the db to various formats)
     * 
     * @throws IOException, IllegalStateException (when not in maintenance mode), InterruptedException
     */
	public static void createDownloads() throws IOException, InterruptedException
    {	
		if(!cpath.isAdminEnabled())
			throw new IllegalStateException("Maintenance mode is not enabled.");
		
		// create downloads directory inside the home dir. if not exist
		File f = new File(cpath.downloadsDir());
		if(!f.exists()) 
			f.mkdir();
    		
		ClassPathXmlApplicationContext context = 
			new ClassPathXmlApplicationContext(new String[] {
					"classpath:META-INF/spring/applicationContext-jpa.xml"});
		
		MetadataRepository metadataRepository = (MetadataRepository) context.getBean(MetadataRepository.class);
		
		// 1) create an imported data summary file.txt (issue#23)
		PrintWriter writer = new PrintWriter(cpath.downloadsDir() + File.separator 
				+ "datasources.txt");
		String date = new SimpleDateFormat("d MMM yyyy").format(Calendar.getInstance().getTime());
		writer.println(StringUtils.join(Arrays.asList(
			"#CPATH2:", getInstance().getName(), "version", getInstance().getVersion(), date), " "));
		writer.println("#Columns:\t" + StringUtils.join(Arrays.asList(
			"ID", "DESCRIPTION", "TYPE", "HOMEPAGE", "PATHWAYS", "INTERACTIONS", "PARTICIPANTS"), "\t"));		
		Iterable<Metadata> allMetadata = metadataRepository.findAll();
		for(Metadata m : allMetadata) {
			writer.println(StringUtils.join(Arrays.asList(
				m.getUri(), m.getDescription(), m.getType(), m.getUrlToHomepage(), 
				m.getNumPathways(), m.getNumInteractions(), m.getNumPhysicalEntities()), "\t"));
		}		
		writer.flush();
		writer.close();	
		// destroy the Spring context, release some resources
		context.close(); context = null;				
		LOG.info("create-downloads: successfully generated the datasources summary file.");
			
		//load the main model
		LOG.info("create-downloads: loading the Main BioPAX Model...");
		Model mainModel = CPathUtils.loadMainBiopaxModel();
		LOG.info("create-downloads: successfully read the Main BioPAX Model");
		
		// generate/find all other BioPAX archives (by organism, etc.):
		List<String> otherBiopaxArchives = createOrFindBiopaxArchives(mainModel, allMetadata);
    	
		// 2) create all other format archives from the main model (still in memory)
		String prefix =  cpath.mainModelFile().substring(0, cpath.mainModelFile().indexOf("BIOPAX."));
		exportBiopaxToOutputFormats(mainModel, prefix);
		mainModel = null; //release quite a few Gygabytes
		
		// 3) export the rest of biopax archives to all other formats		
        for(String biopaxArchive : otherBiopaxArchives) {      	
        	InputStream biopaxStream = null;
        	try {
        		biopaxStream = biopaxStream(biopaxArchive);
        	} catch (IOException e) {
        		LOG.error("Failed to read " + biopaxArchive + "; skipped (wasn't created before?)", e );
        		continue;
        	}       	   		
        	//read the given Model (can be vary large!) from archive
        	LOG.info("Loading the BioPAX model from " + biopaxArchive + "...");
        	Model m = (new SimpleIOHandler()).convertFromOWL(biopaxStream);       	
        	prefix =  biopaxArchive.substring(0, biopaxArchive.indexOf("BIOPAX."));
        	exportBiopaxToOutputFormats(m, prefix);
        }
	}


	private static void exportBiopaxToOutputFormats(final Model m, final String prefix) 
			throws InterruptedException
	{
		//concurrent conversions (different conversions of the same big Model)
		ExecutorService exec = Executors.newCachedThreadPool();
		
    	exec.execute(new Runnable() {
    		public void run() {	
    			String archiveName = prefix + formatAndExt(OutputFormat.GSEA, "uniprot");
    			convertBiopaxToOtherFormatGzipped(m, OutputFormat.GSEA, archiveName); //default to use uniprot AC
    		}
    	});

		exec.execute(new Runnable() {
			public void run() {
				String archiveName = prefix + formatAndExt(OutputFormat.GSEA, "hgnc");
				convertBiopaxToOtherFormatGzipped(m, OutputFormat.GSEA, archiveName, "hgnc symbol");
			}
		});
    	
    	exec.execute(new Runnable() {
    		public void run() { 
    			String extSifArchiveName = prefix + formatAndExt(OutputFormat.EXTENDED_BINARY_SIF, "hgnc");
    			convertBiopaxToOtherFormatGzipped(m, OutputFormat.EXTENDED_BINARY_SIF, extSifArchiveName); //default to use HGNC symbols		
    			//make BINARY_SIF from just generated EXTENDED_BINARY_SIF
    			String sifArchiveName = prefix + formatAndExt(OutputFormat.BINARY_SIF, "hgnc");
    			try {
					convertExtendedSifToSifGzipped(extSifArchiveName, sifArchiveName);
				} catch (IOException e) {
					LOG.error("Failed (skipped) converting " + extSifArchiveName + " to " + sifArchiveName, e );
				}
    		}
    	});

		exec.execute(new Runnable() {
			public void run() {
				String extSifArchiveName = prefix + formatAndExt(OutputFormat.EXTENDED_BINARY_SIF, "uniprot");
				convertBiopaxToOtherFormatGzipped(m, OutputFormat.EXTENDED_BINARY_SIF, extSifArchiveName, "uniprot");
				//make BINARY_SIF from just generated EXTENDED_BINARY_SIF
				String sifArchiveName = prefix + formatAndExt(OutputFormat.BINARY_SIF, "uniprot");
				try {
					convertExtendedSifToSifGzipped(extSifArchiveName, sifArchiveName);
				} catch (IOException e) {
					LOG.error("Failed (skipped) converting " + extSifArchiveName + " to " + sifArchiveName, e );
				}
			}
		});

		exec.shutdown(); //no more tasks
    	//wait until it's done or expired
    	exec.awaitTermination(48, TimeUnit.HOURS);
	}

	//reads from the extended sif archive up to the blank line and writes the interaction lines (edges) to the sif archive
	private static void convertExtendedSifToSifGzipped(String extSifArchiveName, String sifArchiveName)
			throws IOException {

		if((new File(sifArchiveName)).exists()) {
			LOG.info("create-downloads: skip for existing " + sifArchiveName);
			return;
	    } 

		LOG.info("create-downloads: generating new " + sifArchiveName);
		BufferedReader reader = new BufferedReader(new InputStreamReader((new GZIPInputStream(new FileInputStream(extSifArchiveName)))));
		OutputStreamWriter writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(sifArchiveName)));

		//skip the first line (headers)
		if(reader.ready()) reader.readLine();

		while(reader.ready()) {
			String line = reader.readLine();
			//stop at the first blank line (because next come nodes with attributes)
			if(line==null || line.isEmpty())
				break;

			//keep only the first three columns (otherwise, it's not gonna be SIF format)
			writer.write(StringUtils.join(Arrays.copyOfRange(line.split("\t", 4), 0, 3), '\t') + '\n');
		}

		reader.close();
		writer.close();

		LOG.info("create-downloads: successfully created " + sifArchiveName);
	}

	private static Collection<String> findAllUris(Searcher searcher, 
    		Class<? extends BioPAXElement> type, String[] ds, String[] org) 
    {
    	Collection<String> uris = new ArrayList<String>();
    	
    	SearchResponse resp = (SearchResponse) searcher.search("*", 0, type, ds, org);
    	int page = 0;
    	while(!resp.isEmpty()) {
    		for(SearchHit h : resp.getSearchHit())
    			uris.add(h.getUri());
    		//next page
    		resp = (SearchResponse) searcher.search("*", ++page, type, ds, org);
    	}
    	
    	LOG.info("findAllUris(in "+type.getSimpleName()+", ds: "+Arrays.toString(ds)+", org: "+Arrays.toString(org)+") "
    			+ "collected " + uris.size() + " URIs (and the last hits page number was " + (page-1));
    	
    	return uris;
    }

	
	private static void convertBiopaxToOtherFormatGzipped( 
			final Model m, final OutputFormat format, 
			final String archiveName, final Object... params) 
	{	
		if(format == OutputFormat.BIOPAX)
			throw new IllegalArgumentException(format.name() + " is not allowed here.");

		if(format == OutputFormat.GSEA && m.getObjects(Pathway.class).isEmpty()) {
			LOG.info("create-downloads: corresponding BioPAX model has no Pathways; "
					+ "so I will not create " + archiveName);
			return;
		}
		
        if(!(new File(archiveName)).exists()) {
        	LOG.info("create-downloads: generating new " + archiveName);
        	//note: extended SIF will be one file (edges, nodes)
        	try {
        		GZIPOutputStream zos = new GZIPOutputStream(new FileOutputStream(archiveName));
        		convert(m, format, zos, params);
        		zos.flush();
        		IOUtils.closeQuietly(zos); 
        		LOG.info("create-downloads: successfully created " + archiveName);
        	} catch (IOException e) {
        		throw new RuntimeException(e);
        	} 		
        } else {
        	LOG.info("create-downloads: skip for existing " + archiveName);
        }
	}
	
	
	private static List<String> createOrFindBiopaxArchives(final Model mainModel, 
			Iterable<Metadata> allMetadata) throws InterruptedException 
	{
		final List<String> files = new ArrayList<String>();

		// initialize the search engine
		LOG.info("create-downloads: init the full-text search engine...");
		final Searcher searcher = new SearchEngine(mainModel, CPathSettings.getInstance().indexDir());
		
        //collect BioPAX pathway data source names in this set
        Set<String> pathwayDataSources = new HashSet<String>();        
        for(Metadata md : allMetadata) {
        	// collect final biopax archives (skip for warehouse data);
        	if(!md.isNotPathwayData()) {
        		//this BioPAX file already exists (was generated by Merger)
        		files.add(cpath.biopaxExportFileName(md.getIdentifier()));      		
        		//collect if BioPAX data
        		if(md.getType() == METADATA_TYPE.BIOPAX)
            		pathwayDataSources.add(md.standardName());
        	}
        }
		
		//will run concurrently
		final ExecutorService exec = Executors.newCachedThreadPool();
		
    	// export by organism (name)
        if(cpath.getOrganisms().length>1) {
        	LOG.info("create-downloads: preparing data 'by organism' archives...");
        	for(final String org :  cpath.getOrganisms()) {
        		// generate archives for current organism
        		// hack: org.toLowerCase() is to tell by-organism from by-datasource archives (when analyzing the log db) 
        		String organism = org.toLowerCase();
        		String archiveName = cpath.biopaxExportFileName(organism);
        		if(!files.contains(archiveName)) {
        			exportBiopax(exec, mainModel, searcher, archiveName, null, new String[]{organism});
        			files.add(archiveName); 
        		}
        	}
        } else {
        	LOG.info("create-downloads: skipped for 'by organism' archives (there is only one)");
        }
        
        //a separate export - only BioPAX pathway data sources, "Detailed":
        LOG.info("create-downloads: creating 'Detailed' data archives..."); 
        final String archiveName = cpath.biopaxExportFileName("Detailed");
		if(!files.contains(archiveName)) {
			exportBiopax(exec, mainModel, searcher,  archiveName, pathwayDataSources.toArray(new String[]{}), null);
			files.add(archiveName);
		}
        
        exec.shutdown();
        exec.awaitTermination(36, TimeUnit.HOURS);	
        //continue in this thread after the above tasks ended or terminated
		
        return files;
	}	

	
	private static void exportBiopax(ExecutorService exec, 
			final Model mainModel, final Searcher searcher,
			final String biopaxArchive, final String[] datasources, 
			final String[] organisms)
	{
        // check file exists
        if(!(new File(biopaxArchive)).exists()) {
        	LOG.info("create-downloads: creating new " + 	biopaxArchive);
        	
			exec.execute(new Runnable() {			
				public void run() {
					try {
			        	//find all entities (all child elements will be then exported too)    	  	
			       		Collection<String> uris = new HashSet<String>();
			           	uris.addAll(findAllUris(searcher, Pathway.class, datasources, organisms));  	
			           	uris.addAll(findAllUris(searcher, Interaction.class, datasources, organisms));
			           	uris.addAll(findAllUris(searcher, Complex.class, datasources, organisms));
			        	
			    		// export objects found above to a new biopax archive        	
			        	if(!uris.isEmpty()) {
			        		OutputStream os = new GZIPOutputStream(new FileOutputStream(biopaxArchive));
			        		SimpleIOHandler sio = new SimpleIOHandler(BioPAXLevel.L3);
			        		sio.convertToOWL(mainModel, os, uris.toArray(new String[]{}));
			       			LOG.info("create-downloads: successfully created " + 	biopaxArchive);
			        	} else {
			        		LOG.info("create-downloads: no pathways/interactions found; skipping " + 	biopaxArchive);
			        	}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}   						
				}
			});        	
        } else {
        	LOG.info("create-downloads: found previously generated " + biopaxArchive);
        }   		
	}


	private static String formatAndExt(OutputFormat format, String suffix) {
		
		suffix = (suffix==null || suffix.isEmpty()) ? "" : "." + suffix.toLowerCase();
		
		switch (format) {
		case GSEA:
			return format + suffix + ".gmt.gz";
		case SBGN:
			return format + suffix + ".xml.gz";
		case BINARY_SIF:
			return format + suffix + ".sif.gz";
		case EXTENDED_BINARY_SIF:
			return format + suffix + ".sif.gz";
		default://fail - biopax is treated specially, not here
			throw new IllegalArgumentException(format.toString() + " not allowed.");
		}
	}

}
