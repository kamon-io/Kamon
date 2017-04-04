import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import filters.TraceLocalFilter

class Filters @Inject() (traceLocalFilter: TraceLocalFilter) extends DefaultHttpFilters(traceLocalFilter)