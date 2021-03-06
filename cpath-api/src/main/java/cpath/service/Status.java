/**
 ** Copyright (c) 2010 Memorial Sloan-Kettering Cancer Center (MSKCC)
 ** and University of Toronto (UofT).
 **
 ** This is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** both UofT and MSKCC have no obligations to provide maintenance, 
 ** support, updates, enhancements or modifications.  In no event shall
 ** UofT or MSKCC be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** UofT or MSKCC have been advised of the possibility of such damage.  
 ** See the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this software; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA;
 ** or find it at http://www.fsf.org/ or http://www.gnu.org.
 **/

package cpath.service;

import java.util.ArrayList;


public enum Status {
    /**
     * Status Code:  Bad Request, Missing Arguments.
     */
    BAD_REQUEST(452, "Bad Request (illegal or no arguments)"),

    /**
     * Status Code:  No Results Found.
     */
    NO_RESULTS_FOUND(460, "No Results Found"),


    /**
     * Status Code:  Internal Server Error.
     */
    INTERNAL_ERROR(500, "Internal Server Error"),

    
    /**
     * Status Code:  Internal Server Error.
     */
    MAINTENANCE(503, "Server is temporarily unavailable due to regular maintenance");

    private final Integer errorCode;
    private final String errorMsg;
    

	/**
	 * Private Constructor.
	 * 
	 * @param errorCode
	 * @param msg
	 */
    private Status(int errorCode, String msg) {
		this.errorCode = Integer.valueOf(errorCode);
		this.errorMsg = msg;
    }    
	
	
    /**
     * Gets Error Code.
     *
     * @return Error Code.
     */
    public int getErrorCode() {
        return errorCode.intValue();
    }

    /**
     * Gets Error Message.
     *
     * @return Error Message.
     */
    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * Gets Complete List of all Status Codes.
     *
     * @return ArrayList of Status Objects.
     */
    public static ArrayList<String> getAllStatusCodes() {
        ArrayList<String> list = new ArrayList<String>();
        for(Status statusCode : Status.values()) {
        	list.add(statusCode.name());
        }
        return list;
    }

    
	public static Status fromCode(int code) {
		for(Status type : Status.values()) {
			if(type.getErrorCode() == code)
				return type;
		}
		return null;
	} 

}
