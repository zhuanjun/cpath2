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

package cpath.service.analyses;

import cpath.dao.Analysis;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.query.QueryExecuter;
import org.biopax.paxtools.query.algorithm.LimitType;
import org.biopax.paxtools.query.wrapperL3.Filter;
import org.biopax.paxtools.query.wrapperL3.UbiqueFilter;

import java.util.List;
import java.util.Set;

/**
 * Paths-between query. User provides a source set and a target set, the query returns paths from
 * source to target. If no target set is provided, the search is performed between the source set.
 *
 * @author ozgun
 *
 */
public class PathsFromToAnalysis implements Analysis {

	static Log log = LogFactory.getLog(PathsFromToAnalysis.class);
	
	/**
	 * Parameters to provide:
	 * source: IDs of source objects
	 * target: IDs of target objects
	 * limit: search distance limit
	 * limit-type: normal limit or shortest+k limit
	 */
	@Override
	public Set<BioPAXElement> execute(Model model, Object... args)
	{
		// Source elements
		Set<BioPAXElement> source = Common.getAllByID(model, args[0]);

		// Target elements
		Set<BioPAXElement> target = Common.getAllByID(model, args[1]);

		// Search limit
		int limit = (Integer) args[2];

		// organism and data source filters
		List<Filter> filters = Common.getOrganismAndDataSourceFilters(
			(String[]) args[3], (String[]) args[4]);

		// ubique filter
		filters.add(new UbiqueFilter((Set<String>) args[5]));

		// Execute the query

		if (target == null || target.isEmpty())
		{
			return QueryExecuter.runPathsBetween(source, model, limit,
				filters.toArray(new Filter[filters.size()]));
		}
		else
		{
			return QueryExecuter.runPathsFromTo(source, target, model, LimitType.NORMAL, limit,
				filters.toArray(new Filter[filters.size()]));
		}
	}

}