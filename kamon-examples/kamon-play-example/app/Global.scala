import filters.TraceLocalFilter
import play.api.mvc.WithFilters

object Global extends WithFilters(TraceLocalFilter){

}



