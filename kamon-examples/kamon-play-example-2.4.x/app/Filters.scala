import javax.inject.Inject
import play.api.http.HttpFilters
import  filters.TraceLocalFilter

class Filters extends HttpFilters {
  val filters = Seq(TraceLocalFilter)
}