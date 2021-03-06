package cpath.dao;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.maxmind.geoip.Location;
import com.maxmind.geoip.LookupService;

import cpath.config.CPathSettings;
import cpath.jpa.Geoloc;
import cpath.service.OutputFormat;

/**
 * @author rodche
 *
 */
public final class LogUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(LogUtils.class);
	
	static LookupService geolite; 
	
	public static final DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	
	static {
		//will be downloaded if not exists already (one can delete the file to auto-update)
		String localFileName = CPathSettings.getInstance().homeDir() + File.separator + "GeoLiteCity.dat";
		try {
			CPathUtils.download(
				"http://geolite.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz", 
					localFileName, true, false); // - also gunzip			
			geolite = new LookupService(
				localFileName,
				LookupService.GEOIP_MEMORY_CACHE
			);
		} catch (IOException e) {
			throw new RuntimeException("Fauled initializing GeoLite LookupService", e);
		}
	}
	
	final public static Pattern ARCHIVE_SRC_AND_FORMAT_PATTERN = Pattern.compile(".+?[\\s.]\\d+[\\s.](.+?\\..+?)\\..+\\.gz");
	final public static Pattern ARCHIVE_SRC_PATTERN = Pattern.compile(".+?[\\s.]\\d+[\\s.](.+?)\\..+\\.gz");
	final public static Pattern ARCHIVE_FORMAT_PATTERN = Pattern.compile(".+?[\\s.]\\d+[\\s.].+?\\.(.+?)\\..+\\.gz");
	
	protected LogUtils() {
		throw new AssertionError("Not instantiable");
	}
	
	
	/**
	 * Gets a geographical location by IP address
	 * using the GeoLite database.
	 * 
	 * @param ipAddress
	 * @return location or null (when it cannot be found; e.g., if it's local IP)
	 */
	public static Geoloc lookup(String ipAddress) {
		Location geoloc = geolite.getLocation(ipAddress);
		return (geoloc != null) 
			? new Geoloc(geoloc.countryCode, geoloc.region, geoloc.city) 
				: null;
	}
	

	public static String yesterday() {
		Calendar cal = Calendar.getInstance();
	    cal.add(Calendar.DATE, -1);
		Date yesterday = cal.getTime();
		return ISO_DATE_FORMAT.format(yesterday);
	}
	
	public static String today() {
		return ISO_DATE_FORMAT.format(new Date());
	}
	
	static String isoDate(Date date) {
		return ISO_DATE_FORMAT.format(date);
	}

	
	/**
	 * Get a new date (ISO) by adding or 
	 * subtracting (if days < 0) the number 
	 * of days to/from specified date.
	 * 
	 * @param date
	 * @param days
	 * @return
	 */
	static String addIsoDate(Date date, int days) {
		Calendar cal = Calendar.getInstance();    
		cal.setTime(date);    
		cal.add( Calendar.DATE, days );    
		return ISO_DATE_FORMAT.format(cal.getTime()); 
	}
	
	
	/**
	 * Get a new date (ISO) by adding or 
	 * subtracting (if days < 0) the number 
	 * of days to/from specified date.
	 * 
	 * @param date
	 * @param days
	 * @return
	 */
	public static String addIsoDate(String isoDate, int days) {
		Date date;
		try {
			date = ISO_DATE_FORMAT.parse(isoDate);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return addIsoDate(date, days);
	}

	/**
	 * Detects the format of a cpath2 archive 
	 * (from the downloads directory)
	 * 
	 * @param archiveFilename
	 * @return output format, or null if not recognized
	 */
	public static OutputFormat fileOutputFormat(String archiveFilename) {
		OutputFormat of = null;
		Matcher m = ARCHIVE_FORMAT_PATTERN.matcher(archiveFilename);
		if(m.matches()) {
			try {
				of = OutputFormat.valueOf(m.group(1).toUpperCase());
			} catch(IllegalArgumentException e) {
				LOGGER.error("Unknown FORMAT value '" + m.group(1) 
						+ "' in auto-generated " + archiveFilename + " (ignore if it's a test)");
			}//ignore -> of==null
		}
		return of;
	}
	
	/**
	 * For a a cpath2 archive file in the downloads directory,
	 * gets the part of its name that describes data scope that is
	 * either:
	 * - datasource identifier or standard name (older cpath2 versions);
	 * - organism name;
	 * - or special sub-model name, such as 'All', 'Detailed', 'Warehouse'
	 * 
	 * @see Scope
	 * @param archiveFilename
	 * @return name or null if the filename did not match the archive naming pattern.
	 */
	public static String fileSrcOrScope(String archiveFilename) {
		String src = null;
		Matcher m = ARCHIVE_SRC_PATTERN.matcher(archiveFilename);
		if(m.matches()) {
			src = m.group(1);
		}
		return src;
	}
}
