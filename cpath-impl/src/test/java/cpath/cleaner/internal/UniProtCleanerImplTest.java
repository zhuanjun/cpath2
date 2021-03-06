package cpath.cleaner.internal;
/**
 ** Copyright (c) 2009 Memorial Sloan-Kettering Cancer Center (MSKCC)
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


import static org.junit.Assert.*;

import java.io.*;
import java.util.zip.ZipInputStream;

import org.junit.Ignore;
import org.junit.Test;

import cpath.dao.CPathUtils;
import cpath.importer.Cleaner;

/**
 * Test UniProt data cleaner.
 */
@Ignore
public class UniProtCleanerImplTest {

    // some statics of accessions before cleaning
    private static final String CALR3_HUMAN_BEFORE = "AC   Q96L12; Q96LN3; Q53TS5;";
    private static final String CALRL_HUMAN_BEFORE = "AC   Q16602; A8K6G5; A8KAD3; Q53S02; Q53TS5;";

    // some statics of accessions after cleaning
    // note, Q53TS5 is shared accession that will be removed after cleaning
    private static final String CALR3_HUMAN_AFTER = "AC   Q96L12; Q96LN3;";
    private static final String CALRL_HUMAN_AFTER = "AC   Q16602; A8K6G5; A8KAD3; Q53S02;";

	/**
	 * Test method for {@link cpath.converter.internal.UniprotCleanerImpl#cleane(java.lang.String)}.
	 * @throws IOException 
	 */
	@Test
	public void testCleaner() throws IOException {
        // read data from file and look for accessions before being cleaned
        ByteArrayOutputStream os = new ByteArrayOutputStream();
		CPathUtils.unzip(new ZipInputStream(CPathUtils.LOADER
				.getResource("/test_uniprot_data.dat.zip").getInputStream()), os);
        byte[] bytes = os.toByteArray();
        String data = new String(bytes);
        assertTrue(data.indexOf(CALR3_HUMAN_BEFORE) != -1);
        assertTrue(data.indexOf(CALRL_HUMAN_BEFORE) != -1);

		Cleaner cleaner = new UniProtCleanerImpl();
		os.reset();
		cleaner.clean(new ByteArrayInputStream(bytes), os);
		bytes = os.toByteArray();
		
		data = new String(bytes);
        assertTrue(data.indexOf(CALR3_HUMAN_AFTER) != -1);
        assertTrue(data.indexOf(CALRL_HUMAN_AFTER) != -1);
		       
		// dump owl for review
		String outFilename = getClass().getClassLoader().getResource("").getPath() 
			+ File.separator + "testCleanUniProt.out.dat";
        Writer out = new OutputStreamWriter(new FileOutputStream(outFilename));
        out.write(data);
        out.close();
	}

}
